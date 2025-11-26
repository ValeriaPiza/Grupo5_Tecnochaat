const PROXY_URL = 'http://localhost:3002';
const WS_URL = 'ws://localhost:3002';
const AUDIO_WS_BASE = 'ws://localhost:9098/ws/audio';
let ws = null;
let audioWs = null;      //  WebSocket solo para audio de llamadas
let currentUser = localStorage.getItem('tecnochat_username') || '';
let knownGroups = JSON.parse(localStorage.getItem('tecnochat_groups') || '[]');
let activeCallUser = null;
let peerConnections = {};
let localStream = null;
let remoteStream = null;
let signalPoller = null;
let pendingCandidates = {};
let pendingOffers = {};
let ringAudio = null;
let callStartTime = null;
let callTimerInterval = null;
const RTC_CONFIG = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };
let mediaRecorder = null;
let audioContext = null;
let micStream = null;
let micSourceNode = null;
let micProcessorNode = null;
let playbackQueueTime = 0;
const AUDIO_SAMPLE_RATE = 48000;  
let inAudioCall = false;
let recordedChunks = [];

// Conexión WebSocket para actualizaciones en tiempo real (chat, audio, señales RTC)
function connectWebSocket() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        return;
    }
    if (!currentUser) {
        return;
    }
    try {
        ws = new WebSocket(WS_URL);

        ws.onopen = () => {
            console.log('WebSocket conectado al proxy');
        };

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (!data || !data.type) return;

                if (data.type === 'chat-message') {
                    handleIncomingChatMessage(data);
                } else if (data.type === 'audio-note') {
                    handleIncomingAudioNote(data);
                } else if (data.type === 'rtc-signal') {
                    // Señalización de llamada (WebRTC) recibida por WebSocket
                    if (data.to === currentUser) {
                        handleRtcSignal({
                            from: data.from,
                            type: data.signalType,
                            payload: data.payload
                        });
                    }
                }
            } catch (err) {
                console.error('Error procesando mensaje WebSocket:', err);
            }
        };

        ws.onclose = () => {
            console.log('WebSocket cerrado');
            if (currentUser) {
                setTimeout(connectWebSocket, 3000);
            }
        };

        ws.onerror = (err) => {
            console.error('Error en WebSocket:', err);
        };
    } catch (err) {
        console.error('No se pudo abrir WebSocket:', err);
    }
}


function normalizeDescription(desc) {
    if (!desc) return null;
    if (typeof desc === 'object' && desc.type && desc.sdp) {
        return { type: desc.type, sdp: desc.sdp };
    }
    return null;
}

// Navegación entre secciones
function showSection(sectionId, evt) {
    document.querySelectorAll('.section').forEach(section => {
        section.classList.remove('active');
    });

    document.getElementById(sectionId).classList.add('active');

    document.querySelectorAll('.nav button').forEach((button) => {
        button.classList.remove('active');
    });
    if (evt && evt.target) {
        evt.target.classList.add('active');
    }
}

async function startAudioRecording() {
    const recordBtn = document.getElementById('audioRecordBtn');
    const stopBtn = document.getElementById('audioStopBtn');
    const status = document.getElementById('audioStatus');
    const type = document.getElementById('messageType').value;
    const recipient = type === 'private'
        ? document.getElementById('recipientSelect').value
        : document.getElementById('recipientGroupSelect').value;

    if (!currentUser) {
        showStatus('Primero ingresa tu nombre en la parte superior', 'error', document.getElementById('chatStatus'));
        return;
    }
    if (!recipient) {
        showStatus('Selecciona destinatario antes de grabar', 'error', document.getElementById('chatStatus'));
        return;
    }

    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        recordedChunks = [];
        mediaRecorder = new MediaRecorder(stream);
        mediaRecorder.ondataavailable = e => {
            if (e.data.size > 0) recordedChunks.push(e.data);
        };
        mediaRecorder.onstop = () => {
            stream.getTracks().forEach(t => t.stop());
        };
        mediaRecorder.start();
        if (recordBtn) recordBtn.disabled = true;
        if (stopBtn) stopBtn.disabled = false;
        if (status) status.innerHTML = '<span style="color:red; font-weight:700;">● REC</span> Grabando...';
    } catch (err) {
        showStatus('No se pudo acceder al micrófono: ' + err.message, 'error', document.getElementById('chatStatus'));
    }
}

async function stopAndSendAudio() {
    const recordBtn = document.getElementById('audioRecordBtn');
    const stopBtn = document.getElementById('audioStopBtn');
    const status = document.getElementById('audioStatus');
    if (!mediaRecorder) return;

    const recorder = mediaRecorder;
    mediaRecorder = null;

    if (recordBtn) recordBtn.disabled = false;
    if (stopBtn) stopBtn.disabled = true;
    if (status) status.textContent = 'Procesando...';

    await new Promise(resolve => {
        recorder.onstop = () => {
            if (recorder.stream) {
                recorder.stream.getTracks().forEach(t => t.stop());
            }
            resolve();
        };
        recorder.stop();
    });

    if (!recordedChunks.length) {
        showStatus('No se grabó audio', 'error', document.getElementById('chatStatus'));
        if (status) status.textContent = '';
        return;
    }

    const blob = new Blob(recordedChunks, { type: 'audio/webm' });
    recordedChunks = [];
    await sendAudioNote(blob);
    if (status) status.textContent = '';
}

async function sendAudioNote(blob) {
    const type = document.getElementById('messageType').value;
    const recipient = type === 'private'
        ? document.getElementById('recipientSelect').value
        : document.getElementById('recipientGroupSelect').value;
    const statusDiv = document.getElementById('chatStatus');

    if (!currentUser || !recipient) {
        showStatus('Falta destinatario o usuario', 'error', statusDiv);
        return;
    }

    try {
        const arrayBuffer = await blob.arrayBuffer();
        const bytes = new Uint8Array(arrayBuffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        const base64 = btoa(binary);
        const filename = `audio_${Date.now()}.webm`;

        const res = await fetch(`${PROXY_URL}/api/audio/upload`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                to: recipient,
                from: currentUser,
                isGroup: type === 'group',
                filename,
                data: base64
            })
        });
        const result = await res.json();
        if (result.success) {
            showStatus(`Nota de audio enviada a ${recipient}`, 'success', statusDiv);
            loadMessageFeed();
        } else {
            showStatus('No se pudo enviar audio: ' + (result.error || ''), 'error', statusDiv);
        }
    } catch (err) {
        showStatus('Error enviando audio: ' + err.message, 'error', statusDiv);
    }
}

function setUsername() {
    const input = document.getElementById('usernameInput');
    const status = document.getElementById('usernameStatus');
    const btn = document.getElementById('connectBtn');

    // Si ya hay sesión, desconecta
    if (currentUser) {
        status.textContent = `Desconectando...`;
        fetch(`${PROXY_URL}/api/logout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: currentUser })
        }).finally(() => {
            teardownCall(activeCallUser);
            currentUser = '';
            localStorage.removeItem('tecnochat_username');
            input.disabled = false;
            if (btn) btn.textContent = 'Conectar';
            status.textContent = 'Desconectado';
        });
        return;
    }

    const name = input.value.trim();
    if (!name) {
        status.textContent = 'Ingresa un nombre válido';
        return;
    }
    currentUser = name;
    localStorage.setItem('tecnochat_username', currentUser);
    status.textContent = `Conectando...`;

    fetch(`${PROXY_URL}/api/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: currentUser })
    }).then(res => res.json())
      .then(result => {
        if (result.success) {
            status.textContent = `Conectado como ${currentUser}`;
            input.disabled = true;
            if (btn) btn.textContent = 'Desconectar';
            loadOnlineUsers();

            connectWebSocket();       // chat + señales
            connectAudioWebSocket();  // audio de llamadas
            //startSignalPolling();     //  polling como backup

        } else {
            status.textContent = `No se pudo conectar: ${result.error || 'desconocido'}`;
            currentUser = '';
            localStorage.removeItem('tecnochat_username');
            input.disabled = false;
            if (btn) btn.textContent = 'Conectar';
        }
      })
      .catch(err => {
        status.textContent = `Error al conectar: ${err.message}`;
        currentUser = '';
        localStorage.removeItem('tecnochat_username');
        input.disabled = false;
        if (btn) btn.textContent = 'Conectar';
      });
}

// Cambiar etiquetas según tipo de mensaje
document.getElementById('messageType').addEventListener('change', function () {
    const type = this.value;
    const label = document.getElementById('recipientLabel');
    const recipientRow = document.getElementById('recipientRow');
    const recipientSelect = document.getElementById('recipientSelect');
    const groupRow = document.getElementById('groupRow');
    const groupSelect = document.getElementById('recipientGroupSelect');

    if (type === 'private') {
        label.textContent = 'Para (Usuario):';
        if (recipientRow) recipientRow.style.display = 'flex';
        if (groupRow) groupRow.style.display = 'none';
        if (recipientSelect) recipientSelect.style.display = 'block';
        if (groupSelect) groupSelect.style.display = 'none';
        loadMessageFeed();
    } else {
        label.textContent = 'Para (Grupo):';
        if (recipientRow) recipientRow.style.display = 'none';
        if (groupRow) groupRow.style.display = 'flex';
        if (groupSelect) groupSelect.style.display = 'block';
        if (recipientSelect) recipientSelect.style.display = 'none';
        refreshGroupsChat();
        loadMessageFeed();
    }
});

// Cambiar etiquetas en historial
function toggleHistoryInput() {
    const type = document.getElementById('historyType').value;
    const label = document.getElementById('historyLabel');
    const select = document.getElementById('historySelect');

    if (type === 'private') {
        label.textContent = 'Usuario:';
        updateHistoryOptions('private');
    } else {
        label.textContent = 'Grupo:';
        updateHistoryOptions('group');
    }
}

// Enviar mensaje
async function sendMessage(e) {
    const type = document.getElementById('messageType').value;
    const recipient = type === 'private'
        ? document.getElementById('recipientSelect').value
        : document.getElementById('recipientGroupSelect').value;
    const message = document.getElementById('messageText').value;
    const statusDiv = document.getElementById('chatStatus');

    if (!currentUser) {
        showStatus('Primero ingresa tu nombre en la parte superior', 'error', statusDiv);
        return;
    }

    if (!recipient || !message) {
        showStatus('Por favor completa todos los campos', 'error', statusDiv);
        return;
    }

    try {
        const endpoint = type === 'private' ? '/api/messages/private' : '/api/messages/group';
        const body = type === 'private' ?
            { to: recipient, message, from: currentUser } :
            { group: recipient, message, from: currentUser };

        const response = await fetch(PROXY_URL + endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        const result = await response.json();

        if (result.success) {
            if (type === 'group' && recipient) {
                trackGroup(recipient);
            }
            showStatus(result.message, 'success', statusDiv);
            document.getElementById('messageText').value = '';
            loadMessageFeed();
        } else {
            showStatus('Error: ' + (result.error || 'No se pudo enviar'), 'error', statusDiv);
        }
    } catch (error) {
        showStatus('Error de conexión: ' + error.message, 'error', statusDiv);
    }
}

// Crear grupo
async function createGroup() {
    const groupName = document.getElementById('groupName').value;
    const groupMembers = document.getElementById('groupMembers').value;

    const statusDiv = document.getElementById('groupStatus');

    if (!currentUser) {
        showStatus('Primero ingresa tu nombre en la parte superior', 'error', statusDiv);
        return;
    }

    if (!groupName) {
        showStatus('Por favor ingresa un nombre para el grupo', 'error', statusDiv);
        return;
    }

    if (!groupMembers) {
        showStatus('Por favor ingresa un usarios para el grupo', 'error', statusDiv);
        return;
    }

    try {
        const response = await fetch(PROXY_URL + '/api/groups', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: groupName, members: groupMembers, from: currentUser })
        });

        const result = await response.json();

        showStatus(result.success ? (result.message || 'Operación realizada') : ('Error: ' + result.error), result.success ? 'success' : 'error', statusDiv);

        if (result.success && groupName) {
            // guardar grupo local para select de historial
            if (!knownGroups.includes(groupName)) {
                knownGroups.push(groupName);
                localStorage.setItem('tecnochat_groups', JSON.stringify(knownGroups));
            }
            updateHistoryOptions('group');
        }
    } catch (error) {
        showStatus('Error de conexión: ' + error.message, 'error', statusDiv);
    }
}

// Cargar historial
async function loadHistory() {
    const type = document.getElementById('historyType').value;
    const input = document.getElementById('historySelect').value;
    const container = document.getElementById('historyContainer');

    if (!currentUser) {
        container.innerHTML = '<div class="status error">Ingresa tu nombre primero</div>';
        return;
    }

    if (!input) {
        container.innerHTML = '<div class="status error">Por favor ingresa un nombre</div>';
        return;
    }

    try {
        const endpoint = type === 'private' ? '/api/history/private' : '/api/history/group';
        const param = type === 'private' ? 'user' : 'group';
        const requesterParam = type === 'private' ? `&requester=${encodeURIComponent(currentUser)}` : '';

        const response = await fetch(`${PROXY_URL}${endpoint}?${param}=${encodeURIComponent(input)}${requesterParam}`);
        const result = await response.json();

        if (result.success && type === 'group' && input) {
            trackGroup(input);
        }

        if (!result.success) {
            container.innerHTML = `<div class="status error">${result.error || 'Historial no disponible, No hay historial contigo mism@ porque las conversaciones son entre 2 usuarios distintos.'}</div>`;
            return;
        }

        const cleaned = (result.history || []).filter(item => !/webrtc_offer/i.test(item));

        if (cleaned.length > 0) {
            let html = '<h3>Historial:</h3>';
            cleaned.forEach(item => {
                const isAudio = item.includes('[AUDIO:');
                if (isAudio) {
                    // Espera entradas tipo "[AUDIO: <filename>]"
                    const match = item.match(/\[AUDIO:\s*([^\]]+)\]/i);
                    const filename = match ? match[1].trim() : null;
                    html += `<div class="message audio">${item}`;
                    if (filename) {
                        html += `<div style="margin-top:6px;"><audio controls src="${PROXY_URL}/api/audio/download?file=${encodeURIComponent(filename)}"></audio></div>`;
                    }
                    html += `</div>`;
                } else {
                    html += `<div class="message">${item}</div>`;
                }
            });
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="status">No hay historial disponible</div>';
        }
    } catch (error) {
        container.innerHTML = `<div class="status error">Error: ${error.message}</div>`;
    }
}

// Cargar feed de mensajes recientes para el chat actual
function handleIncomingChatMessage(data) {
    const feed = document.getElementById('messageFeed');
    if (!feed) return;
    if (!currentUser) return;

    const typeSelect = document.getElementById('messageType');
    const currentType = typeSelect ? typeSelect.value : 'private';
    const currentRecipient = currentType === 'private'
        ? (document.getElementById('recipientSelect')?.value || '')
        : (document.getElementById('recipientGroupSelect')?.value || document.getElementById('recipientInput')?.value || '');

    if (data.scope === 'private') {
        const involvesMe = (data.to === currentUser || data.from === currentUser);
        const matchesRecipient = !currentRecipient || data.to === currentRecipient || data.from === currentRecipient;
        if (!involvesMe || !matchesRecipient) return;
    } else if (data.scope === 'group') {
        if (currentType !== 'group') return;
        if (currentRecipient && data.group !== currentRecipient) return;
    }

    // Recargamos el historial del chat actual para incluir el nuevo mensaje
    loadMessageFeed();
}

function handleIncomingAudioNote(data) {
    const statusDiv = document.getElementById('chatStatus');
    if (data.scope === 'private') {
        if (data.to === currentUser) {
            showStatus(`Nueva nota de audio de ${data.from}`, 'success', statusDiv);
        }
    } else if (data.scope === 'group') {
        showStatus(`Nueva nota de audio en grupo ${data.group}`, 'success', statusDiv);
    }
    handleIncomingChatMessage(data);
}


// Cargar feed de mensajes recientes para el chat actual
async function loadMessageFeed() {
    const feed = document.getElementById('messageFeed');
    const type = document.getElementById('messageType')?.value || 'private';
    const recipient = type === 'private'
        ? document.getElementById('recipientSelect')?.value
        : document.getElementById('recipientGroupSelect')?.value;

    if (!feed) return;

    if (!currentUser || !recipient) {
        feed.innerHTML = '<div class="status">Selecciona destinatario para ver mensajes</div>';
        return;
    }

    try {
        const endpoint = type === 'private' ? '/api/history/private' : '/api/history/group';
        const param = type === 'private'
            ? `user=${encodeURIComponent(recipient)}&requester=${encodeURIComponent(currentUser)}`
            : `group=${encodeURIComponent(recipient)}`;

        const response = await fetch(`${PROXY_URL}${endpoint}?${param}`);
        const result = await response.json();

        if (!result.success) {
            feed.innerHTML = `<div class="status error">${result.error || 'No se pudo cargar mensajes'}</div>`;
            return;
        }

        const messages = (result.history || []).filter(msg => !/webrtc_offer/i.test(msg));
        if (messages.length === 0) {
            feed.innerHTML = '<div class="status">Sin mensajes</div>';
            return;
        }

        feed.innerHTML = messages.map(msg => {
            const isAudio = msg.includes('[AUDIO:');
            if (isAudio) {
                const match = msg.match(/\[AUDIO:\s*([^\]]+)\]/i);
                const filename = match ? match[1].trim() : null;
                return `<div class="message audio">${msg}${filename ? `<div style="margin-top:6px;"><audio controls src="${PROXY_URL}/api/audio/download?file=${encodeURIComponent(filename)}"></audio></div>` : ''}</div>`;
            }
            return `<div class="message">${msg}</div>`;
        }).join('');
    } catch (error) {
        feed.innerHTML = `<div class="status error">Error: ${error.message}</div>`;
    }
}

// Cargar usuarios conectados
async function loadOnlineUsers() {
    const container = document.getElementById('usersContainer');

    try {
        const response = await fetch(PROXY_URL + '/api/users/online');
        const result = await response.json();
        const users = (result.success && Array.isArray(result.users)) ? result.users : [];

        if (container) {
            if (users.length > 0) {
                const html = users.map(user => `<div class="user-card">${user}</div>`).join('');
                container.innerHTML = html || '<div class="status">No hay usuarios conectados</div>';
            } else {
                container.innerHTML = '<div class="status">No hay usuarios conectados</div>';
            }
        }
        updateRecipientSelect(users);
        updateHistoryOptions('private', users);
        updateCallUsers(users);
    } catch (error) {
        if (container) {
            container.innerHTML = `<div class="status error">Error: ${error.message}</div>`;
        }
        updateRecipientSelect([]);
        updateHistoryOptions('private', []);
        updateCallUsers([]);
    }
}

function updateRecipientSelect(users) {
    const select = document.getElementById('recipientSelect');
    if (!select) return;

    select.onchange = () => loadMessageFeed();

    const previous = select.value;
    const safeUsers = (users || []).filter(u => u && u !== currentUser);
    select.innerHTML = '';
    select.disabled = false;

    if (safeUsers.length === 0) {
        select.innerHTML = '<option value=\"\">No hay usuarios conectados</option>';
        return;
    }

    const options = ['<option value=\"\">Selecciona un usuario conectado</option>']
        .concat(safeUsers.map(u => `<option value=\"${u}\">${u}</option>`));
    select.innerHTML = options.join('');

    if (previous && safeUsers.includes(previous)) {
        select.value = previous;
    }
}

function refreshRecipients() {
    loadOnlineUsers();
}

async function refreshGroupsChat() {
    try {
        const res = await fetch(`${PROXY_URL}/api/groups`);
        const data = await res.json();
        const groups = (data.success && Array.isArray(data.groups)) ? data.groups : [];
        const select = document.getElementById('recipientGroupSelect');
        if (select) {
            const previous = select.value;
            select.innerHTML = ['<option value="">Selecciona un grupo</option>']
                .concat(groups.map(g => `<option value="${g}">${g}</option>`))
                .join('');
            if (previous && groups.includes(previous)) {
                select.value = previous;
            }
            select.onchange = () => loadMessageFeed();
        }
    } catch (err) {
        console.error('Error cargando grupos:', err);
    }
}

function updateHistoryOptions(type, usersFromLoad) {
    const select = document.getElementById('historySelect');
    if (!select) return;

    const isPrivate = (type || document.getElementById('historyType').value) === 'private';
    if (!isPrivate) {
        fetchGroupsForHistory();
        return;
    }

    const source = usersFromLoad || [];

    const previous = select.value;
    select.innerHTML = '';

    if (!source || source.length === 0) {
        select.innerHTML = `<option value="">No hay ${isPrivate ? 'usuarios' : 'grupos'} disponibles</option>`;
        select.disabled = true;
        return;
    }

    select.disabled = false;
    const options = [`<option value="">Selecciona un ${isPrivate ? 'usuario' : 'grupo'}</option>`]
        .concat(source.map(item => `<option value="${item}">${item}</option>`));
    select.innerHTML = options.join('');

    if (previous && source.includes(previous)) {
        select.value = previous;
    }
}

function trackGroup(name) {
    const clean = (name || '').trim();
    if (!clean) return;
    if (!knownGroups.includes(clean)) {
        knownGroups.push(clean);
        localStorage.setItem('tecnochat_groups', JSON.stringify(knownGroups));
        updateHistoryOptions('group');
    }
}

async function fetchGroupsForHistory() {
    const select = document.getElementById('historySelect');
    if (!select) return;
    select.disabled = true;
    select.innerHTML = '<option>Cargando grupos...</option>';

    try {
        const res = await fetch(`${PROXY_URL}/api/groups`);
        const data = await res.json();
        const list = (data.success && Array.isArray(data.groups)) ? data.groups : [];

        const previous = select.value;
        if (!list.length) {
            select.innerHTML = '<option value=\"\">No hay grupos disponibles</option>';
            return;
        }

        knownGroups = list;
        localStorage.setItem('tecnochat_groups', JSON.stringify(knownGroups));

        const options = ['<option value=\"\">Selecciona un grupo</option>']
            .concat(list.map(g => `<option value=\"${g}\">${g}</option>`));
        select.innerHTML = options.join('');
        select.disabled = false;
        if (previous && list.includes(previous)) {
            select.value = previous;
        }
    } catch (error) {
        select.innerHTML = `<option value=\"\">Error al cargar grupos</option>`;
    }
}

// Miembros de grupo
async function loadGroupMembers() {
    const group = document.getElementById('groupName').value;
    const statusDiv = document.getElementById('groupStatus');
    if (!group) {
        showStatus('Ingresa un grupo para ver sus miembros', 'error', statusDiv);
        return;
    }

    try {
        const response = await fetch(`${PROXY_URL}/api/groups/${encodeURIComponent(group)}/members`);
        const result = await response.json();
        if (result.success) {
            const members = result.members || [];
            showStatus(`Miembros (${members.length}): ${members.join(', ') || 'Ninguno'}`, 'success', statusDiv);
        } else {
            showStatus('Error: ' + (result.error || 'No se pudo obtener miembros'), 'error', statusDiv);
        }
    } catch (error) {
        showStatus('Error de conexión: ' + error.message, 'error', statusDiv);
    }
}

async function loadGroupsList() {
    const container = document.getElementById('groupsList');
    const statusDiv = document.getElementById('groupStatus');
    if (!container) return;
    container.innerHTML = '<div class="status">Cargando grupos...</div>';
    try {
        const res = await fetch(`${PROXY_URL}/api/groups`);
        const data = await res.json();
        if (!data.success) {
            container.innerHTML = `<div class="status error">${data.error || 'No se pudo obtener grupos'}</div>`;
            return;
        }
        const groups = data.groups || [];
        if (groups.length === 0) {
            container.innerHTML = '<div class="status">No hay grupos</div>';
        } else {
            container.innerHTML = groups.map(g => `<div class="message">${g}</div>`).join('');
        }
        if (statusDiv && data.success) {
            showStatus(`Grupos disponibles: ${groups.length}`, 'success', statusDiv);
        }
    } catch (error) {
        container.innerHTML = `<div class="status error">Error: ${error.message}</div>`;
        showStatus('Error de conexión: ' + error.message, 'error', statusDiv);
    }
}

function connectAudioWebSocket() {
    if (!currentUser) return;
    if (audioWs && (audioWs.readyState === WebSocket.OPEN || audioWs.readyState === WebSocket.CONNECTING)) {
        return;
    }

    const url = `${AUDIO_WS_BASE}/${encodeURIComponent(currentUser)}`;
    audioWs = new WebSocket(url);
    audioWs.binaryType = 'arraybuffer';

    audioWs.onopen = () => {
        console.log(' Audio WS conectado para', currentUser);
        if (!audioContext) {
            audioContext = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: AUDIO_SAMPLE_RATE
            });
            playbackQueueTime = audioContext.currentTime;
        }
    };

    audioWs.onmessage = (event) => {
        handleIncomingAudioChunk(event.data);
    };

    audioWs.onclose = () => {
        console.log(' Audio WS cerrado');
        audioWs = null;
    };

    audioWs.onerror = (err) => {
        console.error('Error en Audio WS:', err);
    };
}

async function startAudioStreaming() {
    if (!audioWs || audioWs.readyState !== WebSocket.OPEN) {
        console.warn('Audio WS no está conectado, no se puede iniciar streaming');
        return;
    }

    inAudioCall = true;  

    try {
        if (!audioContext) {
            audioContext = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: AUDIO_SAMPLE_RATE
            });
        }

        if (!micStream) {
            micStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
        }

        micSourceNode = audioContext.createMediaStreamSource(micStream);

        const bufferSize = 2048;
        micProcessorNode = audioContext.createScriptProcessor(bufferSize, 1, 1);

        micProcessorNode.onaudioprocess = (audioProcessingEvent) => {
            if (!inAudioCall) return; 

            const inputBuffer = audioProcessingEvent.inputBuffer;
            const inputData = inputBuffer.getChannelData(0); // Float32

            const pcmBuffer = new ArrayBuffer(inputData.length * 2);
            const pcmView = new DataView(pcmBuffer);

            for (let i = 0; i < inputData.length; i++) {
                let s = Math.max(-1, Math.min(1, inputData[i]));
                s = s < 0 ? s * 0x8000 : s * 0x7FFF;
                pcmView.setInt16(i * 2, s, true); // Int16 little-endian
            }

            if (audioWs && audioWs.readyState === WebSocket.OPEN) {
                audioWs.send(pcmBuffer);
            }
        };

        micSourceNode.connect(micProcessorNode);
        micProcessorNode.connect(audioContext.destination);


        console.log(' Streaming de audio iniciado');
    } catch (err) {
        console.error('No se pudo iniciar captura de audio:', err);
    }
}




function stopAudioStreaming() {
    try {
        inAudioCall = false;  //  marca que ya no hay llamada

        if (micProcessorNode) {
            micProcessorNode.disconnect();
            micProcessorNode.onaudioprocess = null;
            micProcessorNode = null;
        }
        if (micSourceNode) {
            micSourceNode.disconnect();
            micSourceNode = null;
        }
        if (micStream) {
            micStream.getTracks().forEach(t => t.stop());
            micStream = null;
        }

        if (audioContext) {
            playbackQueueTime = audioContext.currentTime; // resetea la cola
        }

        console.log('Streaming de audio detenido');
    } catch (err) {
        console.error('Error al detener streaming de audio:', err);
    }
}





function handleIncomingAudioChunk(arrayBuffer) {
    // Si no hay llamada activa, ignora audio entrante
    if (!inAudioCall) {
        return;
    }

    if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)({
            sampleRate: AUDIO_SAMPLE_RATE
        });
        playbackQueueTime = audioContext.currentTime;
    }

    const pcmView = new DataView(arrayBuffer);
    const sampleCount = pcmView.byteLength / 2;
    const float32Data = new Float32Array(sampleCount);

    for (let i = 0; i < sampleCount; i++) {
        const int16 = pcmView.getInt16(i * 2, true);
        float32Data[i] = int16 < 0 ? (int16 / 0x8000) : (int16 / 0x7FFF);
    }

    const buffer = audioContext.createBuffer(1, sampleCount, AUDIO_SAMPLE_RATE);
    buffer.copyToChannel(float32Data, 0, 0);

    const source = audioContext.createBufferSource();
    source.buffer = buffer;

    const now = audioContext.currentTime;
    const startTime = Math.max(playbackQueueTime, now);

    source.connect(audioContext.destination);
    source.start(startTime);

    playbackQueueTime = startTime + buffer.duration;
}






async function sendRtcSignal(to, type, data) {
    if (!currentUser || !to || !type) return;
    await fetch(`${PROXY_URL}/api/webrtc/signal`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ to, from: currentUser, type, data })
    });
}



function startRing(from) {
    if (!ringAudio) {
        
        ringAudio = new Audio('./ring.mp3');
        ringAudio.loop = true;
    }
    ringAudio.currentTime = 0;
    ringAudio.play().catch(() => {});

    const incoming = document.getElementById('incomingCall');
    if (incoming) {
        incoming.style.display = 'flex';
        incoming.innerHTML = `
             Llamada entrante de <strong>${from}</strong>
            <button class="btn" onclick="acceptCall('${from}')">Contestar</button>
            <button class="btn" style="background:#dc3545" onclick="rejectCall('${from}')">Rechazar</button>
        `;
    }
}

function stopRing() {
    if (ringAudio) {
        ringAudio.pause();
        ringAudio.currentTime = 0;
    }
    const incoming = document.getElementById('incomingCall');
    if (incoming) {
        incoming.style.display = 'none';
        incoming.innerHTML = '';
    }
}

function startCallTimer() {
    callStartTime = Date.now();
    const timerDiv = document.getElementById('callTimer');
    const hangBtn = document.getElementById('callHangBtn');
    if (!timerDiv) return;
    timerDiv.style.display = 'block';
    if (hangBtn) {
        hangBtn.style.display = 'inline-block';
        hangBtn.onclick = () => endCallUser(activeCallUser);
    }
    callTimerInterval = setInterval(() => {
        const elapsed = Date.now() - callStartTime;
        const minutes = String(Math.floor(elapsed / 60000)).padStart(2, '0');
        const seconds = String(Math.floor((elapsed % 60000) / 1000)).padStart(2, '0');
        timerDiv.textContent = `Duración: ${minutes}:${seconds}`;
    }, 1000);
}

function stopCallTimer() {
    if (callTimerInterval) {
        clearInterval(callTimerInterval);
        callTimerInterval = null;
    }
    const timerDiv = document.getElementById('callTimer');
    const hangBtn = document.getElementById('callHangBtn');
    if (timerDiv) {
        timerDiv.style.display = 'none';
        timerDiv.textContent = 'Duración: 00:00';
    }
    if (hangBtn) {
        hangBtn.style.display = 'none';
        hangBtn.onclick = null;
    }
    callStartTime = null;
}



function teardownCall(user, message) {
    const statusDiv = document.getElementById('callStatus');
    const target = user || activeCallUser;

    // Detener captura y reproducción de audio
    stopAudioStreaming();

    // Limpiar estado interno
    activeCallUser = null;
    inAudioCall = false;      

    // Detener sonido de llamada entrante
    stopRing();

    // Detener temporizador de llamada
    stopCallTimer();

    // Limpiar UI de llamada entrante
    const incoming = document.getElementById('incomingCall');
    if (incoming) {
        incoming.style.display = 'none';
        incoming.innerHTML = '';
    }

    // Limpiar UI del temporizador
    const timerDiv = document.getElementById('callTimer');
    if (timerDiv) {
        timerDiv.style.display = 'none';
        timerDiv.textContent = 'Duración: 00:00';
    }

    // Actualizar lista de usuarios (botones de llamar/colgar)
    loadCallUsers();

    //  Mensaje
    if (message) {
        showStatus(message, 'info', statusDiv || document.getElementById('callStatus'));
    }
}



function handleRtcSignal(signal) {
    try {
        const { from, type } = signal || {};
        if (!from || !type) return;

        const statusDiv = document.getElementById('callStatus');

        if (type === 'call-offer') {
            // Llamada entrante
            startRing(from);
            showStatus(`Llamada entrante de ${from}`, 'success', statusDiv);
            return;
        }

        if (type === 'call-accept') {
            // como el otro usuario acepto la llamada iniciamos el audio
            activeCallUser = from;
            showStatus(`En llamada con ${from}`, 'success', statusDiv);
            startAudioStreaming();
            startCallTimer();     // ahora mostramos el botón de colgar
            loadCallUsers();      // refrescamos tarjetas para que el boton quede activo
            return;
        }

        if (type === 'call-reject') {
            showStatus(`Llamada rechazada por ${from}`, 'error', statusDiv);
            stopRing();
            teardownCall(from);
            return;
        }

        if (type === 'hangup') {
            stopAudioStreaming();
            teardownCall(from, `Llamada finalizada por ${from}`);
            return;
        }
    } catch (err) {
        const statusDiv = document.getElementById('callStatus') || document.createElement('div');
        showStatus('Error procesando señal de llamada: ' + err.message, 'error', statusDiv);
    }
}


async function acceptCall(from) {
    stopRing();
    if (!from) return;
    const statusDiv = document.getElementById('callStatus');

    try {
        activeCallUser = from;
        await sendRtcSignal(from, 'call-accept', {});
        showStatus(`En llamada con ${from}`, 'success', statusDiv);

        startAudioStreaming();
        startCallTimer();   //  mostrar botón de colgar
        loadCallUsers();    //  refrescar lista
    } catch (err) {
        showStatus('Error al contestar: ' + err.message, 'error', statusDiv || document.createElement('div'));
        activeCallUser = null;
    }
}




async function rejectCall(from) {
    stopRing();
    const statusDiv = document.getElementById('callStatus');
    if (!from) {
        showStatus('No hay llamada para rechazar', 'error', statusDiv);
        return;
    }
    try {
        await sendRtcSignal(from, 'call-reject', {});
        showStatus(`Llamada rechazada a ${from}`, 'success', statusDiv);
    } catch (err) {
        showStatus('Error al rechazar: ' + err.message, 'error', statusDiv || document.createElement('div'));
    }
}




async function loadCallUsers() {
    // reutiliza loadOnlineUsers para mantener fuentes consistentes
    await loadOnlineUsers();
}

function updateCallUsers(users) {
    const container = document.getElementById('callUsersContainer');
    if (!container) return;

    const list = (users || []).filter(u => u && u !== currentUser);

    if (list.length === 0) {
        container.innerHTML = '<div class="status">No hay usuarios disponibles para llamar</div>';
        return;
    }

    const cards = list.map(user => {
        const isActive = activeCallUser === user;
        const disableCall = !!activeCallUser && !isActive;
        const disableHang = !isActive;
        return `
            <div class="call-card">
                <span>${user}</span>
                <div class="call-actions">
                    <button class="btn-icon btn-call" ${disableCall ? 'disabled' : ''} onclick="startCallUser('${user}')">📞</button>
                    <button class="btn-icon btn-hang" ${disableHang ? 'disabled' : ''} onclick="endCallUser('${user}')">⛔</button>
                </div>
            </div>
        `;
    }).join('');

    container.innerHTML = cards;
}

async function startCallUser(user) {
    const statusDiv = document.getElementById('callStatus');
    if (!user) {
        showStatus('Selecciona un usuario', 'error', statusDiv);
        return;
    }
    if (!currentUser) {
        showStatus('Primero ingresa tu nombre en la parte superior', 'error', statusDiv);
        return;
    }
    if (activeCallUser && activeCallUser !== user) {
        showStatus(`Ya hay una llamada con ${activeCallUser}. Cuelga primero.`, 'error', statusDiv);
        return;
    }

    try {
        activeCallUser = user;
        await sendRtcSignal(user, 'call-offer', {});
        showStatus(`Llamando a ${user}...`, 'success', statusDiv);
        loadCallUsers();
    } catch (error) {
        activeCallUser = null;
        showStatus('Error iniciando llamada: ' + error.message, 'error', statusDiv);
    }
}


async function endCallUser(user) {
    const statusDiv = document.getElementById('callStatus');
    if (!activeCallUser) {
        showStatus('No hay llamada en curso', 'error', statusDiv);
        return;
    }
    const target = user || activeCallUser;
    try {
        await sendRtcSignal(target, 'hangup', {});
        stopAudioStreaming();
        teardownCall(target);
        showStatus(`Llamada terminada con ${target}`, 'success', statusDiv);
        loadCallUsers();
    } catch (error) {
        showStatus('Error al terminar la llamada: ' + error.message, 'error', statusDiv);
    }
}


// Mostrar estado
function showStatus(message, type, container) {
    container.innerHTML = `<div class="status ${type}">${message}</div>`;
    setTimeout(() => {
        container.innerHTML = '';
    }, 5000);
}
// Agregar event listeners para navegación
document.addEventListener('DOMContentLoaded', function () {
    // Limpia estado previo para evitar sesiones obsoletas
    localStorage.clear();
    currentUser = '';
    knownGroups = [];
    connectWebSocket();
    activeCallUser = null;

    document.querySelectorAll('.nav button').forEach(button => {
        button.addEventListener('click', function (evt) {
            const sectionId = this.getAttribute('data-section');
            showSection(sectionId, evt);
        });
    });

    // precargar nombre si existe
    const usernameInput = document.getElementById('usernameInput');
    const status = document.getElementById('usernameStatus');
    const btn = document.getElementById('connectBtn');
    if (currentUser && usernameInput && status) {
        usernameInput.value = currentUser;
        usernameInput.disabled = true;
        status.textContent = `Conectado como ${currentUser}`;
        if (btn) btn.textContent = 'Desconectar';
    } else if (btn) {
        btn.textContent = 'Conectar';
    }

    updateHistoryOptions(); // inicializar select de historial
    loadCallUsers();
    loadMessageFeed();
    refreshGroupsChat();
});

function sendLogoutBeacon(user) {
    if (!user) return;
    const url = `${PROXY_URL}/api/logout`;
    const payload = JSON.stringify({ username: user });
    if (navigator.sendBeacon) {
        const blob = new Blob([payload], { type: 'application/json' });
        navigator.sendBeacon(url, blob);
    } else {
        fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: payload,
            keepalive: true
        }).catch(() => {});
    }
}

window.addEventListener('beforeunload', () => {
    if (currentUser) {
        sendLogoutBeacon(currentUser);
    }
});

window.loadOnlineUsers = loadOnlineUsers;
window.loadHistory = loadHistory;
window.showSection = showSection;
window.sendMessage = sendMessage;
window.createGroup = createGroup;
window.toggleHistoryInput = toggleHistoryInput;
window.loadGroupMembers = loadGroupMembers;
window.loadGroupsList = loadGroupsList;
window.setUsername = setUsername;
window.refreshRecipients = refreshRecipients;
window.updateHistoryOptions = updateHistoryOptions;
window.fetchGroupsForHistory = fetchGroupsForHistory;
window.loadCallUsers = loadCallUsers;
window.startCallUser = startCallUser;
window.endCallUser = endCallUser;
window.acceptCall = acceptCall;
window.rejectCall = rejectCall;
window.startAudioRecording = startAudioRecording;
window.stopAndSendAudio = stopAndSendAudio;
function refreshHistoryOptions() {
    toggleHistoryInput();
}
window.refreshHistoryOptions = refreshHistoryOptions;
