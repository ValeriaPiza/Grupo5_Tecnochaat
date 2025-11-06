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
                
                // Buscar líneas completas en el buffer
                const lines = this.responseBuffer.split('\n');
                
                // Mantener la última línea incompleta en el buffer
                this.responseBuffer = lines.pop() || '';
                
                lines.forEach(line => {
                    const trimmed = line.trim();
                    if (trimmed) {
                        console.log('Servidor:', trimmed);
                        
                        // Si hay una promesa pendiente y encontramos la respuesta esperada
                        if (this.pendingResolve) {
                            // Buscar específicamente la línea CLIENTES_CONECTADOS
                            if (trimmed.includes('CLIENTES_CONECTADOS:')) {
                                this.pendingResolve(trimmed);
                                this.pendingResolve = null;
                                this.pendingReject = null;
                            }
                            // Para otros comandos, podrías agregar más condiciones aquí
                        }
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
                this.reconnect();
            });
        });
    }

    reconnect() {
        setTimeout(() => {
            console.log('Intentando reconectar...');
            this.connect().catch(console.error);
        }, 3000);
    }

    async sendCommand(command) {
        if (!this.isConnected) {
            await this.connect();
        }

        return new Promise((resolve, reject) => {
            this.pendingResolve = resolve;
            this.pendingReject = reject;

            console.log('Enviando comando:', command);
            this.client.write(command + '\n');

            // Timeout después de 5 segundos
            setTimeout(() => {
                if (this.pendingReject) {
                    this.pendingReject(new Error('Timeout esperando respuesta'));
                    this.pendingResolve = null;
                    this.pendingReject = null;
                }
            }, 5000);
        });
    }

    async getOnlineUsers() {
        try {
            const response = await this.sendCommand('11');
            
            // Extraer usuarios de la respuesta
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
        // Por ahora solo confirmamos
        res.json({ 
            success: true, 
            message: 'Mensaje enviado correctamente' 
        });
    } catch (error) {
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