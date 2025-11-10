import express from 'express';
import cors from 'cors';
import net from 'net';

const app = express();
const PORT = 3001;
const JAVA_SERVER_HOST = 'localhost';
const JAVA_SERVER_PORT = 6789;

app.use(cors());
app.use(express.json());

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

    }

    async connect() {
        return new Promise((resolve, reject) => {
            if (this.isConnected && this.client) {
                resolve();
                return;
            }

            this.client = new net.Socket();
            this.responseBuffer = '';

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

    handleServerResponse(trimmed) {
        console.log('Manejando respuesta:', trimmed);
        
        // Manejar respuestas interactivas
        if (this.expectingInteractiveResponse && this.interactiveResolve) {
            // Si recibimos un error, rechazar
            if (trimmed.includes('Opcion no valida') || trimmed.includes('Error')) {
                console.log('Error detectado, rechazando...');
                this.interactiveReject(new Error(trimmed));
                this.resetInteractiveState();
                return;
            }
            
            // 🔥 DETECTAR PETICIONES ESPECÍFICAS DEL SERVIDOR
            if (trimmed.includes('Ingresa el nombre del destinatario:') || 
                trimmed.includes('Ingresa el mensaje:') ||
                trimmed.includes('Elige opcion:')) {
                
                console.log('Servidor está pidiendo información, procesando siguiente paso...');
                this.waitingForPrompt = false;
                this.processNextInteractiveStep();
                return;
            }
            
            // Si recibimos el menú completo, puede ser que el servidor esté listo para el siguiente paso
            if (trimmed.includes('=== MENU TECNOCHAT ===')) {
                console.log('Menú recibido, esperando prompt específico...');
                this.waitingForPrompt = true;
                return;
            }
            
            // Si estamos en el paso final y recibimos confirmación
            if (this.interactiveStep >= this.interactiveCommands.length && 
                !trimmed.includes('=== MENU TECNOCHAT ===')) {
                console.log('Comando completado exitosamente');
                this.interactiveResolve('Mensaje enviado correctamente');
                this.resetInteractiveState();
            }
        }
        
        // Manejar respuestas normales
        if (this.pendingResolve && trimmed.includes('CLIENTES_CONECTADOS:')) {
            this.pendingResolve(trimmed);
            this.pendingResolve = null;
            this.pendingReject = null;
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
            
            // Iniciar el primer paso inmediatamente
            this.processNextInteractiveStep();
            
            // Timeout más largo para interacción
            setTimeout(() => {
                if (this.interactiveReject) {
                    this.interactiveReject(new Error('Timeout en comando interactivo'));
                    this.resetInteractiveState();
                }
            }, 20000);
        });
    }

    processNextInteractiveStep() {
        if (this.interactiveStep < this.interactiveCommands.length) {
            const command = this.interactiveCommands[this.interactiveStep];
            console.log(`Enviando paso ${this.interactiveStep + 1}/${this.interactiveCommands.length}:`, command);
            
            // Pequeño delay para asegurar que el servidor esté listo
            setTimeout(() => {
                this.client.write(command + '\n');
                this.interactiveStep++;
                
                // Si no es el último paso, esperar prompt específico
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
            
            // 🔥 ENVIAR SOLO EL COMANDO INICIAL PRIMERO
            await this.sendCommand('1');
            
            // Esperar un momento para que el servidor procese
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            // 🔥 LUEGO ENVIAR EL DESTINATARIO
            console.log('Enviando destinatario:', to);
            await this.sendCommand(to);
            
            // Esperar un momento
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            // 🔥 FINALMENTE ENVIAR EL MENSAJE
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
            // Para comandos simples, no esperamos una respuesta específica
            console.log('Enviando comando simple:', command);
            this.client.write(command + '\n');
            
            // Resolver inmediatamente para comandos de interacción
            // (el servidor manejará la conversación)
            setTimeout(() => {
                resolve('Comando enviado');
            }, 300);
        });
    }



    async getOnlineUsers() {
        try {
            const response = await this.sendCommand('11');
            
            if (response.includes('CLIENTES_CONECTADOS:')) {
                const usersPart = response.split('CLIENTES_CONECTADOS:')[1];
                if (usersPart && usersPart.trim() !== 'No hay otros clientes conectados.') {
                    const users = usersPart.split(',').map(user => user.trim()).filter(user => user);
                    return users;
                }
            }
            return [];
        } catch (error) {
            console.error('Error obteniendo usuarios:', error);
            return [];
        }
    }

    reconnect() {
        setTimeout(() => {
            console.log('Intentando reconectar...');
            this.connect().catch(console.error);
        }, 3000);
    }
}
// Crear cliente persistente
const persistentClient = new PersistentTCPClient(JAVA_SERVER_HOST, JAVA_SERVER_PORT);

// Conectar al iniciar
persistentClient.connect().then(() => {
    console.log('Cliente persistente conectado y listo');
}).catch(console.error);

// Endpoint para usuarios online - SIMPLIFICADO
app.get('/api/users/online', async (req, res) => {
    try {
        const users = await persistentClient.getOnlineUsers();
        res.json({
            success: true,
            users: users
        });
    } catch (error) {
        console.error('Error en endpoint /api/users/online:', error);
        res.json({ 
            success: true, 
            users: [] 
        });
    }
});

// Endpoints para otros comandos (simplificados por ahora)
app.post('/api/messages/private', async (req, res) => {
    try {
        const { to, message } = req.body;
        
        console.log('📤 MENSAJE PRIVADO ENVIADO:');
        console.log('   Para:', to);
        console.log('   Mensaje:', message);
        console.log('   Timestamp:', new Date().toLocaleString());
        
        // Usar el nuevo método específico para mensajes privados
        const result = await persistentClient.sendPrivateMessage(to, message);
        
        res.json({ 
            success: true, 
            message: 'Mensaje enviado correctamente' 
        });
    } catch (error) {
        console.error('❌ Error enviando mensaje privado:', error);
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

app.post('/api/messages/group', async (req, res) => {
    try {
        const { group, message } = req.body;
        res.json({ 
            success: true, 
            message: 'Mensaje grupal enviado' 
        });
    } catch (error) {
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

app.post('/api/groups', async (req, res) => {
    try {
        const { name } = req.body;
        res.json({ 
            success: true, 
            message: 'Grupo creado correctamente' 
        });
    } catch (error) {
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

app.get('/api/history/private', async (req, res) => {
    try {
        const { user } = req.query;
        res.json({ 
            success: true, 
            history: [] 
        });
    } catch (error) {
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

app.get('/api/history/group', async (req, res) => {
    try {
        const { group } = req.query;
        res.json({ 
            success: true, 
            history: [] 
        });
    } catch (error) {
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

app.listen(PORT, () => {
    console.log(`Proxy HTTP mejorado corriendo en http://localhost:${PORT}`);
    console.log(`Conectando persistentemente al servidor Java en ${JAVA_SERVER_HOST}:${JAVA_SERVER_PORT}`);
});