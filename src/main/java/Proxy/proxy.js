import express from 'express';
import cors from 'cors';
import net from 'net';

const app = express();
const PORT = 3001;
const JAVA_SERVER_HOST = 'localhost';
const JAVA_SERVER_PORT = 6789;

app.use(cors());
app.use(express.json());

class TCPClient {
    constructor(host, port) {
        this.host = host;
        this.port = port;
    }

    async sendCommand(commands){
        return new Promise((resolve,  reject) => {
            const client = new net.Socket();
            let responses = [];
            let commandIndex = 0;
            let buffer = '';

            client.connect(this.port, this.host, () => {
                this.sendNextCommand(client, commands, commandIndex);
                commandIndex++;
            } );

            client.on('data', (data) => {
                buffer += data.toString();
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                lines.forEach(line => {
                    const trimmed = line.trim();
                    if (trimmed) {
                        responses.push(trimmed),
                        console.log('Server:', trimmed);
                    }
                });

                if (commandIndex < commands.length && responses.length >= commandIndex) {
                    this.sendNextCommand(client, commands, commandIndex);
                    commandIndex++;
                }
            });

            client.on('close', () => {
                resolve(responses);
            });

            client.on('error', reject);
            
            setTimeout(() => {
                client.destroy();
                reject(new Error('Timeout'));
            }, 5000);
        });
    }

    sendNextCommand(client, commands, index) {
        if(index < commands.length) {
            console.log('Client:', commands[index]);
            client.write(commands[index] + '\n');
        }
    }
}

const tcpClient = new TCPClient(JAVA_SERVER_HOST, JAVA_SERVER_PORT);

app.get('/api/users/online', async (req, res) => {
    try {
        const commands = ['11'];
        const responses = await tcpClient.sendCommand(commands);

        const users = responses.filter(line => 
            line.includes('CLIENTES_CONECTADOS:' ) ||
            (line.startsWith('-') && !line.includes('==='))
        );
        
        res.json({
            success: true,
            users: users
        });
    } catch (error) {
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

app.listen(PORT, () => {
    console.log(`Proxy HTTP corriendo en http://localhost:${PORT}`);
    console.log(`Conectando al servidor Java en ${JAVA_SERVER_HOST}:${JAVA_SERVER_PORT}`);
});