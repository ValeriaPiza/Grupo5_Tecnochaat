import express from 'express';
import cors from 'cors';
import PersistentTCPClient from "./PersistentTCPClient.js"

const app = express();
const PORT = 3001;
const JAVA_SERVER_HOST = 'localhost';
const JAVA_SERVER_PORT = 6789;

app.use(cors());
app.use(express.json());

// cliente persistente
const persistentClient = new PersistentTCPClient(JAVA_SERVER_HOST, JAVA_SERVER_PORT);

// Conectar al iniciar
persistentClient.connect().then(() => {
    console.log('Cliente persistente conectado y listo');
}).catch(console.error);


// ENDPOINTS TECNOCHAT


// 1. OBTENER USUARIOS EN LÍNEA
app.get('/api/users/online', async (req, res) => {
    try {
        console.log(' Obteniendo usuarios en línea...');
        const users = await persistentClient.getOnlineUsers();

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

// 2. ENVIAR MENSAJE PRIVADO
app.post('/api/messages/private', async (req, res) => {
    try {
        const { to, message } = req.body;

        // Valida los datos
        if (!to || !message) {
            return res.status(400).json({
                success: false,
                error: 'Faltan parámetros requeridos: to y message'
            });
        }

        console.log(' ENVIANDO MENSAJE PRIVADO:');
        console.log('   De: WebCliente');
        console.log('   Para:', to);
        console.log('   Mensaje:', message);
        console.log('   Timestamp:', new Date().toLocaleString());

        const result = await persistentClient.sendPrivateMessage(to, message);

        console.log(' Mensaje privado enviado exitosamente');

        res.json({
            success: true,
            message: 'Mensaje enviado correctamente',
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
        const { group, message } = req.body;
        console.log(req.body)
        
        if (!group || !message) {
            return res.status(400).json({
                success: false,
                error: 'Faltan parámetros requeridos: group y message'
            });
        }

        console.log(' ENVIANDO MENSAJE A GRUPO:');
        console.log('   De: WebCliente');
        console.log('   Grupo:', group);
        console.log('   Mensaje:', message);
        console.log('   Timestamp:', new Date().toLocaleString());

        const result = await persistentClient.sendGroupMessage(group, message);

        console.log(' Mensaje grupal enviado exitosamente');

        res.json({
            success: true,
            message: 'Mensaje enviado al grupo correctamente',
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

// 4. CREAR GRUPO
app.post('/api/groups', async (req, res) => {
    try {
        
        const { name, members } = req.body;
        
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
        console.log('   Creador: WebCliente');
        console.log('   Miembros:', members);
        console.log('   Timestamp:', new Date().toLocaleString());

        const result = await persistentClient.createGroup(name, members);

        console.log(' Grupo creado exitosamente');

        res.json({
            success: true,
            message: 'Grupo creado correctamente',
            groupName: name,
            members: Array.isArray(members) ? members : members.split(',').map(m => m.trim()),
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        console.error(' Error creando grupo:', error);
        res.status(500).json({
            success: false,
            error: error.message,
            details: 'No se pudo crear el grupo'
        });
    }
});

// 5. enpoint para obtenr historial de los chats privados 
app.get('/api/history/private', async (req, res) => {
    try {
        const { user } = req.query;

        
        if (!user) {
            return res.status(400).json({
                success: false,
                error: 'El parámetro user es requerido'
            });
        }

        console.log(' OBTENIENDO HISTORIAL PRIVADO:');
        console.log('   Usuario actual: WebCliente');
        console.log('   Usuario consulta:', user);
        console.log('   Timestamp:', new Date().toLocaleString());

        const history = await persistentClient.getPrivateHistory(user);

        console.log(' Historial obtenido:', history.length, 'mensajes');

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

// 6. Historial del grupo
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

        const history = await persistentClient.getGroupHistory(group);

        console.log(' Historial de grupo obtenido:', history.length, 'mensajes');

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
        connected: persistentClient.isConnected,
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
    console.log(`Proxy HTTP mejorado corriendo en http://localhost:${PORT}`);
    console.log(`Conectando persistentemente al servidor Java en ${JAVA_SERVER_HOST}:${JAVA_SERVER_PORT}`);
});