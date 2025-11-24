import express from 'express';
import cors from 'cors';
import path from 'path';
import { fileURLToPath } from 'url';
import IceClient from './IceClient.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = parseInt(process.env.PORT || '3002', 10);
const ICE_HOST = process.env.ICE_HOST || 'localhost';
const ICE_PORT = parseInt(process.env.ICE_PORT || '10000', 10);

app.use(cors());
app.use(express.json());

// Cliente Ice persistente
const iceClient = new IceClient({ host: ICE_HOST, port: ICE_PORT });
iceClient.connect().then(() => {
    console.log('Cliente Ice conectado y listo');
}).catch(err => {
    console.error('Error conectando cliente Ice:', err);
});


// ENDPOINTS TECNOCHAT


// 1. OBTENER USUARIOS EN LÍNEA
app.get('/api/users/online', async (req, res) => {
    try {
        console.log(' Obteniendo usuarios en línea...');
        const users = await iceClient.getOnlineUsers();

        console.log(' Usuarios encontrados:', users.length);

        res.json({
            success: true,
            users: users,
            count: users.length,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error en /api/users/online:', error);
        res.json({
            success: true,
            users: [],
            count: 0,
            error: error.message
        });
    }
});

// LOGIN
app.post('/api/login', async (req, res) => {
    try {
        const { username } = req.body;
        if (!username) {
            return res.status(400).json({ success: false, error: 'username requerido' });
        }
        const ok = await iceClient.login(username);
        res.json({ success: ok, username });
    } catch (error) {
        console.error(' Error en /api/login:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

// LOGOUT
app.post('/api/logout', async (req, res) => {
    try {
        const { username } = req.body;
        if (!username) {
            return res.status(400).json({ success: false, error: 'username requerido' });
        }
        await iceClient.logout(username);
        res.json({ success: true, username });
    } catch (error) {
        console.error(' Error en /api/logout:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

// 2. ENVIAR MENSAJE PRIVADO
app.post('/api/messages/private', async (req, res) => {
    try {
        const { to, message, from } = req.body;
        const sender = from || req.headers['x-username'] || 'WebCliente';

        // Valida los datos
        if (!to || !message) {
            return res.status(400).json({
                success: false,
                error: 'Faltan parámetros requeridos: to y message'
            });
        }

        console.log(' ENVIANDO MENSAJE PRIVADO:');
        console.log('   De:', sender);
        console.log('   Para:', to);
        console.log('   Mensaje:', message);
        console.log('   Timestamp:', new Date().toLocaleString());

        await iceClient.sendMessage(sender, to, message);

        console.log(' Mensaje privado enviado exitosamente');

        res.json({
            success: true,
            message: 'Mensaje enviado correctamente',
            from: sender,
            to: to,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error('Error enviando mensaje privado:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            details: 'No se pudo enviar el mensaje al servidor'
        });
    }
});

// 3. ENVIAR MENSAJE A GRUPO
app.post('/api/messages/group', async (req, res) => {
    try {
        const { group, message, from } = req.body;
        const sender = from || req.headers['x-username'] || 'WebCliente';
        console.log(req.body)
        
        if (!group || !message) {
            return res.status(400).json({
                success: false,
                error: 'Faltan parámetros requeridos: group y message'
            });
        }

        console.log(' ENVIANDO MENSAJE A GRUPO:');
        console.log('   De:', sender);
        console.log('   Grupo:', group);
        console.log('   Mensaje:', message);
        console.log('   Timestamp:', new Date().toLocaleString());

        await iceClient.sendGroupMessage(sender, group, message);

        console.log(' Mensaje grupal enviado exitosamente');

        res.json({
            success: true,
            message: 'Mensaje enviado al grupo correctamente',
            from: sender,
            group: group,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error enviando mensaje grupal:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            details: 'No se pudo enviar el mensaje al grupo'
        });
    }
});

// 4. INICIAR LLAMADA
app.post('/api/calls/start', async (req, res) => {
    try {
        const { to, from } = req.body;
        const caller = from || req.headers['x-username'] || 'WebCliente';

        if (!to) {
            return res.status(400).json({
                success: false,
                error: 'Faltan parámetros requeridos: to'
            });
        }

        console.log(' INICIANDO LLAMADA (Ice):', caller, '->', to);
        await iceClient.startCall(caller, to);

        res.json({
            success: true,
            message: 'Llamada iniciada',
            to,
            from: caller,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error iniciando llamada:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            details: 'No se pudo iniciar la llamada via Ice'
        });
    }
});

// 5. TERMINAR LLAMADA
app.post('/api/calls/end', async (req, res) => {
    try {
        const { user } = req.body;
        const target = user || req.headers['x-username'] || 'WebCliente';

        console.log(' TERMINANDO LLAMADA (Ice) para:', target);
        await iceClient.endCall(target);

        res.json({
            success: true,
            message: 'Llamada terminada',
            user: target,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error terminando llamada:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            details: 'No se pudo terminar la llamada via Ice'
        });
    }
});

// 5b. Señalización WebRTC: enviar señal
app.post('/api/webrtc/signal', async (req, res) => {
    try {
        const { to, from, type, data } = req.body;
        const sender = from || req.headers['x-username'] || 'WebCliente';

        if (!to || !type) {
            return res.status(400).json({
                success: false,
                error: 'Parámetros requeridos: to, type'
            });
        }

        await iceClient.sendRtcSignal(sender, to, type, typeof data === 'string' ? data : JSON.stringify(data ?? {}));
        res.json({ success: true });
    } catch (error) {
        console.error(' Error enviando señal WebRTC:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'No se pudo enviar la señal'
        });
    }
});

// 5c. Señalización WebRTC: obtener señales pendientes para usuario
app.get('/api/webrtc/signals', async (req, res) => {
    try {
        const user = req.query.user || req.headers['x-username'] || null;
        if (!user) {
            return res.status(400).json({ success: false, error: 'user requerido' });
        }

        const rawSignals = await iceClient.pullRtcSignals(user);
        const signals = (rawSignals || []).map(raw => {
            try {
                const parsed = JSON.parse(raw);
                // payload viene como string json; intentar parsear
                if (parsed && typeof parsed.payload === 'string') {
                    try {
                        parsed.payload = JSON.parse(parsed.payload);
                    } catch (_) { /* deja string */ }
                }
                return parsed;
            } catch (_) {
                return { raw };
            }
        }).filter(Boolean);

        res.json({ success: true, signals });
    } catch (error) {
        console.error(' Error obteniendo señales WebRTC:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'No se pudo obtener señales',
            signals: []
        });
    }
});

// 6. CREAR GRUPO
app.post('/api/groups', async (req, res) => {
    try {
        
        const { name, members, from } = req.body;
        const creator = from || req.headers['x-username'] || 'WebCliente';
        
        if (!name) {
            return res.status(400).json({
                success: false,
                error: 'El nombre del grupo es requerido'
            });
        }
        console.log({ name, members})
        if (!members || (Array.isArray(members) && members.length === 0)) {
            return res.status(400).json({
                success: false,
                error: 'Debe especificar al menos un miembro para el grupo'
            });
        }

        console.log(' CREANDO GRUPO:');
        console.log('   Nombre:', name);
        console.log('   Creador:', creator);
        console.log('   Miembros:', members);
        console.log('   Timestamp:', new Date().toLocaleString());

        const membersList = Array.isArray(members) ? members.slice() : (members || "").split(',').map(m => m.trim()).filter(Boolean);
        if (creator && !membersList.includes(creator)) {
            membersList.push(creator);
        }

        const result = await iceClient.createGroup(name, membersList);
        if (result) {
            console.log(' Grupo creado via Ice');
            res.json({
                success: true,
                message: 'Grupo creado correctamente',
                groupName: name,
                members: membersList,
                timestamp: new Date().toISOString()
            });
        } else {
            res.status(500).json({
                success: false,
                error: 'No se pudo crear el grupo'
            });
        }
    } catch (error) {
        console.error(' Error creando grupo:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            details: 'No se pudo crear el grupo'
        });
    }
});

// 7. OBTENER MIEMBROS DE GRUPO
app.get('/api/groups/:group/members', async (req, res) => {
    try {
        const group = req.params.group;

        if (!group) {
            return res.status(400).json({
                success: false,
                error: 'El parámetro group es requerido'
            });
        }

        console.log(' OBTENIENDO MIEMBROS DE GRUPO (Ice):', group);
        const members = await iceClient.getGroupMembers(group);

        res.json({
            success: true,
            group,
            members,
            count: members.length,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error obteniendo miembros de grupo:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            members: [],
            details: 'No se pudieron obtener los miembros del grupo'
        });
    }
});

// 7b. OBTENER LISTA DE GRUPOS
app.get('/api/groups', async (req, res) => {
    try {
        const groups = await iceClient.getGroups();
        res.json({
            success: true,
            groups,
            count: groups.length,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error obteniendo grupos:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            groups: []
        });
    }
});

// 8. enpoint para obtenr historial de los chats privados 
app.get('/api/history/private', async (req, res) => {
    try {
        const { user, requester } = req.query;
        const currentUser = requester || req.headers['x-username'] || null;


        if (!user) {
            return res.status(400).json({
                success: false,
                error: 'El parámetro user es requerido'
            });
        }
        // Si no se envía requester, se asume el usuario actual desde el header o vacío
        const requesterUser = currentUser || '';

        console.log(' OBTENIENDO HISTORIAL PRIVADO:');
        console.log('   Usuario actual:', requesterUser);
        console.log('   Usuario consulta:', user);
        console.log('   Timestamp:', new Date().toLocaleString());

        const rawHistory = await iceClient.getPrivateHistory(requesterUser, user);
        const history = (rawHistory || []).filter(line => !/webrtc_offer/i.test(line));

        res.json({
            success: true,
            user: user,
            history: history,
            count: history.length,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error obteniendo historial privado:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            history: [],
            details: 'No se pudo obtener el historial'
        });
    }
});

// 8b. Subir nota de audio (base64) para guardarla en backend
app.post('/api/audio/upload', async (req, res) => {
    try {
        const { to, from, isGroup, filename, data } = req.body;
        const sender = from || req.headers['x-username'] || 'WebCliente';

        if (!to || !data) {
            return res.status(400).json({ success: false, error: 'Parámetros requeridos: to, data' });
        }

        const buffer = Buffer.from(data, 'base64');
        const ok = await iceClient.sendAudio(sender, to, !!isGroup, filename || 'audio.webm', new Uint8Array(buffer));
        res.json({ success: ok });
    } catch (error) {
        console.error(' Error subiendo nota de audio:', error);
        res.status(500).json({ success: false, error: error.message || 'No se pudo subir audio' });
    }
});

// 8c. Descargar/servir audio histórico
app.get('/api/audio/download', async (req, res) => {
    try {
        const { file } = req.query;
        if (!file) {
            return res.status(400).json({ success: false, error: 'file requerido' });
        }
        // Archivos se guardan en backend-java/audio_history (ruta absoluta segura)
        const audioPath = path.join(__dirname, '..', '..', 'backend-java', 'audio_history', file);
        return res.sendFile(audioPath, (err) => {
            if (err) {
                console.error(' Error enviando archivo de audio:', err);
                if (!res.headersSent) {
                    res.status(404).json({ success: false, error: 'Audio no encontrado' });
                }
            }
        });
    } catch (error) {
        console.error(' Error descargando audio:', error);
        res.status(500).json({ success: false, error: error.message || 'No se pudo descargar audio' });
    }
});

// 9. Historial del grupo
app.get('/api/history/group', async (req, res) => {
    try {
        const { group } = req.query;

        
        if (!group) {
            return res.status(400).json({
                success: false,
                error: 'El parámetro group es requerido'
            });
        }

        console.log(' OBTENIENDO HISTORIAL DE GRUPO:');
        console.log('   Grupo:', group);
        console.log('   Timestamp:', new Date().toLocaleString());

        const history = await iceClient.getGroupHistory(group);

        res.json({
            success: true,
            group: group,
            history: history,
            count: history.length,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error obteniendo historial de grupo:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            history: [],
            details: 'No se pudo obtener el historial del grupo'
        });
    }
});

// 7. ENDPOINT (Health Check)
app.get('/api/health', (req, res) => {
    res.json({
        success: true,
        status: 'running',
        ice: {
            connected: iceClient.connected,
            host: ICE_HOST,
            port: ICE_PORT
        },
        timestamp: new Date().toISOString()
    });
});

// 8. Este es el manejo de errores global 
app.use((err, req, res, next) => {
    console.error(' Error no manejado:', err);
    res.status(500).json({
        success: false,
        error: 'Error interno del servidor',
        message: err.message
    });
});

app.listen(PORT, () => {
    console.log(`Proxy HTTP  en http://localhost:${PORT}`);
    console.log(`Conectando via Ice al servidor Java en ${ICE_HOST}:${ICE_PORT}`);
});
