import net from 'net';

class PersistentTCPClient {
    constructor(host, port) {
        this.host = host;
        this.port = port;
        this.client = null;
        this.isConnected = false;
        this.responseBuffer = '';
        this.pendingResolve = null;
        this.pendingReject = null;
        this.commandQueue = [];
        this.isProcessing = false;
        this.expectingInteractiveResponse = false;
        this.interactiveCommands = [];
        this.interactiveStep = 0;
        this.interactiveResolve = null;
        this.interactiveReject = null;
        this.waitingForPrompt = false;

        this.nameSent = false;
    }

    async connect() {
        return new Promise((resolve, reject) => {
            if (this.isConnected && this.client) {
                resolve();
                return;
            }

            this.client = new net.Socket();
            this.responseBuffer = '';
            this.nameSent = false; // Resetear al reconectar

            this.client.connect(this.port, this.host, () => {
                console.log('Conectado persistentemente al servidor Java');
                this.isConnected = true;
                resolve();
            });

            this.client.on('data', (data) => {
                this.responseBuffer += data.toString();

                const lines = this.responseBuffer.split('\n');
                this.responseBuffer = lines.pop() || '';

                lines.forEach(line => {
                    const trimmed = line.trim();
                    if (trimmed) {
                        console.log('Servidor:', trimmed);
                        this.handleServerResponse(trimmed);
                    }
                });
            });

            this.client.on('close', () => {
                console.log('Conexión cerrada - Reconectando...');
                this.isConnected = false;
                this.reconnect();
            });

            this.client.on('error', (err) => {
                console.error('Error TCP:', err);
                this.isConnected = false;
                if (this.pendingReject) {
                    this.pendingReject(err);
                    this.pendingResolve = null;
                    this.pendingReject = null;
                }
                if (this.interactiveReject) {
                    this.interactiveReject(err);
                    this.interactiveResolve = null;
                    this.interactiveReject = null;
                }
                this.reconnect();
            });
        });
    }

    async handleServerResponse(trimmed) {

        // IGNORAR LÍNEAS DEL MENÚ - No son respuestas relevantes
        const menuLines = [
            '=== MENU TECNOCHAT ===',
            '1. Enviar mensaje a usuario',
            '2. Crear grupo',
            '3. Enviar mensaje a grupo',
            '4. Salir',
            '5. Nota de voz privada',
            '6. Nota de voz a grupo',
            '7. Ver historial privado',
            '8. Ver historial de grupo',
            '9. Llamar a un usuario/grupo',
            '10. Terminar llamada',
            '11. Ver clientes en linea',
            '======================',
            'Elige opcion:'
        ];

        if (menuLines.some(line => trimmed.includes(line))) {
            return;
        }

        console.log('Manejando respuesta:', trimmed);

        // DETECTAR Y ENVIAR NOMBRE AUTOMÁTICAMENTE (solo una vez)
        if (!this.nameSent && (trimmed.includes('Ingresa tu nombre') || trimmed.includes('Ingrea tu Nombre'))) {
            console.log('Enviando nombre: WebCliente');
            this.client.write('WebCliente\n');
            this.nameSent = true;
            return;
        }

        // Capturar historial privado
        if (this.waitingForHistory && trimmed.includes('=== HISTORIAL CON')) {
            this.historyBuffer = [];
            this.capturingHistory = true;
            return;
        }

        if (this.capturingHistory) {
            if (trimmed.includes('=== FIN DEL HISTORIAL ===')) {
                this.capturingHistory = false;
                if (this.historyResolve) {
                    this.historyResolve(this.historyBuffer);
                    this.historyResolve = null;
                }
                this.waitingForHistory = false;
                return;
            }
            this.historyBuffer.push(trimmed);
            return;
        }

        // Capturar historial de grupo
        if (this.waitingForGroupHistory && trimmed.includes('=== HISTORIAL DEL GRUPO')) {
            this.groupHistoryBuffer = [];
            this.capturingGroupHistory = true;
            return;
        }

        if (this.capturingGroupHistory) {
            if (trimmed.includes('=== FIN DEL HISTORIAL ===')) {
                this.capturingGroupHistory = false;
                if (this.groupHistoryResolve) {
                    this.groupHistoryResolve(this.groupHistoryBuffer);
                    this.groupHistoryResolve = null;
                }
                this.waitingForGroupHistory = false;
                return;
            }
            this.groupHistoryBuffer.push(trimmed);
            return;
        }

        
        if (this.expectingInteractiveResponse && this.interactiveResolve) {
            console.log('Modo interactivo activo, paso:', this.interactiveStep);

            // Si recibimos un error, rechazar
            if (trimmed.includes('Opcion no valida') || trimmed.includes('Error')) {
                console.log('Error detectado, rechazando...');
                if (this.interactiveReject) {
                    this.interactiveReject(new Error(trimmed));
                }
                this.resetInteractiveState();
                return;
            }

            // DETECTAR PETICIONES ESPECÍFICAS DEL SERVIDOR
            if (trimmed.includes('A que usuario deseas enviar el mensaje?') ||
                trimmed.includes('Escribe tu mensaje:') ||
                trimmed.includes('Nombre del grupo:') ||
                trimmed.includes('Escribe los nombres de los usuarios') ||
                trimmed.includes('Nombre del grupo al que deseas enviar mensaje:') ||
                trimmed.includes('Escribe tu mensaje para el grupo:') ||
                trimmed.includes('Elige opcion:')) {

                console.log('Servidor pidiendo información, procesando siguiente paso...');
                this.waitingForPrompt = false;
                this.processNextInteractiveStep();
                return;
            }

            // Confirmaciones exitosas
            if (trimmed.includes('Mensaje enviado correctamente') ||
                trimmed.includes('Grupo') && trimmed.includes('creado') ||
                trimmed.includes('Mensaje enviado al grupo correctamente')) {
                console.log('Operación completada exitosamente');
                if (this.interactiveResolve) {
                    this.interactiveResolve({ success: true, message: trimmed });
                }
                this.resetInteractiveState();
                return;
            }

            // Si estamos en el paso final
            if (this.interactiveStep >= this.interactiveCommands.length) {
                setTimeout(() => {
                    if (this.interactiveResolve && this.expectingInteractiveResponse) {
                        this.interactiveResolve({ success: true, message: 'Operación completada' });
                        this.resetInteractiveState();
                    }
                }, 1000);
            }
            return;
        }

        
        if (this.pendingResolve) {
            
            if (trimmed.includes('CLIENTES_CONECTADOS:')) {
                console.log('Respuesta de clientes recibida');
                this.pendingResolve(trimmed);
                return;
            }
        }
    }

    async sendInteractiveCommand(commands) {
        if (!this.isConnected) {
            await this.connect();
        }

        return new Promise((resolve, reject) => {
            this.expectingInteractiveResponse = true;
            this.interactiveCommands = commands;
            this.interactiveStep = 0;
            this.interactiveResolve = resolve;
            this.interactiveReject = reject;
            this.waitingForPrompt = false;

            console.log('Iniciando comando interactivo con pasos:', commands);

            
            this.processNextInteractiveStep();


        });
    }

    processNextInteractiveStep() {
        if (this.interactiveStep < this.interactiveCommands.length) {
            const command = this.interactiveCommands[this.interactiveStep];
            console.log(`Enviando paso ${this.interactiveStep + 1}/${this.interactiveCommands.length}:`, command);

            
            setTimeout(() => {
                this.client.write(command + '\n');
                this.interactiveStep++;

                
                if (this.interactiveStep < this.interactiveCommands.length) {
                    this.waitingForPrompt = true;
                }
            }, 500);
        } else {
            console.log('Todos los pasos completados, esperando confirmación final...');
        }
    }

    resetInteractiveState() {
        this.expectingInteractiveResponse = false;
        this.interactiveCommands = [];
        this.interactiveStep = 0;
        this.interactiveResolve = null;
        this.interactiveReject = null;
        this.waitingForPrompt = false;
    }

    async sendPrivateMessage(to, message) {
        try {
            console.log('Preparando mensaje privado para:', to);

            
            await this.sendCommand('1');

            
            await new Promise(resolve => setTimeout(resolve, 1000));

        
            console.log('Enviando destinatario:', to);
            await this.sendCommand(to);

            
            await new Promise(resolve => setTimeout(resolve, 1000));

            
            console.log('Enviando mensaje:', message);
            await this.sendCommand(message);

            return { success: true, message: 'Mensaje enviado correctamente' };

        } catch (error) {
            console.error('Error enviando mensaje privado:', error);
            throw error;
        }
    }

    async sendCommand(command) {
        if (!this.isConnected) {
            await this.connect();
        }

        return new Promise((resolve, reject) => {
            
            console.log('Enviando comando simple:', command);
            this.client.write(command + '\n');


            setTimeout(() => {
                resolve('Comando enviado');
            }, 300);
        });
    }

    async getOnlineUsers() {
        try {
            console.log("Solicitando usuarios en línea...");

            if (!this.isConnected) {
                await this.connect();
            }

            return new Promise((resolve, reject) => {
                
                this.pendingResolve = resolve;
                this.pendingReject = reject;

                
                this.client.write('11\n');


            }).then(response => {
               
                if (typeof response === 'string' && response.includes('CLIENTES_CONECTADOS:')) {
                    const usersPart = response.split('CLIENTES_CONECTADOS:')[1];
                    if (usersPart && usersPart.trim() !== 'No hay otros clientes conectados.') {
                        const users = usersPart.split(',')
                            .map(user => user.trim())
                            .filter(user => user && user.length > 0);
                        console.log('Usuarios encontrados:', users);
                        return users;
                    }
                }
                console.log('No hay usuarios conectados');
                return [];
            });
        } catch (error) {
            console.error('Error obteniendo usuarios:', error);
            return [];
        }
    }

    async getPrivateHistory(user) {
        try {
            console.log(' Obteniendo historial privado con:', user);

            if (!this.isConnected) {
                await this.connect();
            }

            return new Promise((resolve, reject) => {
                this.waitingForHistory = true;
                this.historyBuffer = [];
                this.historyResolve = resolve;

                this.client.write('7\n');

                setTimeout(() => {
                    this.client.write(user + '\n');
                }, 800);

                setTimeout(() => {
                    if (this.waitingForHistory) {
                        console.log('  Timeout obteniendo historial');
                        this.waitingForHistory = false;
                        this.capturingHistory = false;
                        resolve([]);
                    }
                }, 8000);
            });
        } catch (error) {
            console.error(' Error obteniendo historial privado:', error);
            throw error;
        }
    }

    async getGroupHistory(group) {
        try {
            console.log(' Obteniendo historial de grupo:', group);

            if (!this.isConnected) {
                await this.connect();
            }

            return new Promise((resolve, reject) => {
                this.waitingForGroupHistory = true;
                this.groupHistoryBuffer = [];
                this.groupHistoryResolve = resolve;

                this.client.write('8\n');

                setTimeout(() => {
                    this.client.write(group + '\n');
                }, 800);

                setTimeout(() => {
                    if (this.waitingForGroupHistory) {
                        console.log('  Timeout obteniendo historial de grupo');
                        this.waitingForGroupHistory = false;
                        this.capturingGroupHistory = false;
                        resolve([]);
                    }
                }, 8000);
            });
        } catch (error) {
            console.error(' Error obteniendo historial de grupo:', error);
            throw error;
        }
    }

    async createGroup(groupName, members) {
        try {
            console.log('🔨 Creando grupo:', groupName);
            const membersString = Array.isArray(members) ? members.join(',') : members;
            const result = await this.sendInteractiveCommand(['2', groupName, membersString]);
            return result;
        } catch (error) {
            console.error('❌ Error creando grupo:', error);
            throw error;
        }
    }

    async sendGroupMessage(group, message) {
        try {
            console.log(' Enviando mensaje a grupo:', group);
            const result = await this.sendInteractiveCommand(['3', group, message]);
            return result;
        } catch (error) {
            console.error(' Error enviando mensaje a grupo:', error);
            throw error;
        }
    }

    reconnect() {
        setTimeout(() => {
            console.log('Intentando reconectar...');
            this.connect().catch(console.error);
        }, 3000);
    }
}

export default PersistentTCPClient;