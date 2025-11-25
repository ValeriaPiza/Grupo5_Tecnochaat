import { Ice } from "ice";

class IceClient {
    constructor({ host = "localhost", port = 10000 } = {}) {
        this.host = host;
        this.port = port;
        this.communicator = null;
        this.chatPrx = null;
        this.callPrx = null;
        this.connected = false;
    }

    async connect() {
        if (this.connected && this.communicator) {
            return;
        }

        this.communicator = Ice.initialize();
        this.chatPrx = this._stringToProxy("ChatService");
        this.callPrx = this._stringToProxy("CallService");
        this.connected = true;
        console.log(`[IceClient] Conectado a ${this.host}:${this.port}`);
    }

    async close() {
        if (this.communicator) {
            await this.communicator.destroy();
        }
        this.connected = false;
        this.communicator = null;
        this.chatPrx = null;
        this.callPrx = null;
    }

    async getOnlineUsers() {
        await this._ensureConnected();
        return this._invokeStringSeq(this.chatPrx, "getOnlineUsers");
    }

    async getGroupMembers(group) {
        await this._ensureConnected();
        return this._invokeStringSeq(this.chatPrx, "getGroupMembers", (os) => {
            os.writeString(group ?? "");
        });
    }

    async getGroupHistory(group) {
        await this._ensureConnected();
        return this._invokeStringSeq(this.chatPrx, "getGroupHistory", (os) => {
            os.writeString(group ?? "");
        });
    }

    async getGroups() {
        await this._ensureConnected();
        return this._invokeStringSeq(this.chatPrx, "getGroups");
    }

    async getPrivateHistory(requester, other) {
        await this._ensureConnected();
        return this._invokeStringSeq(this.chatPrx, "getPrivateHistory", (os) => {
            os.writeString(requester ?? "");
            os.writeString(other ?? "");
        });
    }

    async login(username) {
        await this._ensureConnected();
        return this._invokeBool(this.chatPrx, "login", (os) => {
            os.writeString(username ?? "");
        });
    }

    async logout(username) {
        await this._ensureConnected();
        return this._invokeVoid(this.chatPrx, "logout", (os) => {
            os.writeString(username ?? "");
        });
    }

    async sendMessage(from, to, message) {
        await this._ensureConnected();
        return this._invokeVoid(this.chatPrx, "sendMessage", (ostr) => {
            ostr.writeString(from ?? "");
            ostr.writeString(to ?? "");
            ostr.writeString(message ?? "");
        });
    }

    async sendGroupMessage(from, group, message) {
        await this._ensureConnected();
        return this._invokeVoid(this.chatPrx, "sendGroupMessage", (ostr) => {
            ostr.writeString(from ?? "");
            ostr.writeString(group ?? "");
            ostr.writeString(message ?? "");
        });
    }

    async startCall(from, to) {
        await this._ensureConnected();
        return this._invokeVoid(this.callPrx, "startCall", (ostr) => {
            ostr.writeString(from ?? "");
            ostr.writeString(to ?? "");
        });
    }

    async endCall(user) {
        await this._ensureConnected();
        return this._invokeVoid(this.callPrx, "endCall", (ostr) => {
            ostr.writeString(user ?? "");
        });
    }

    async sendRtcSignal(from, to, type, payload) {
        await this._ensureConnected();
        return this._invokeVoid(this.callPrx, "sendRtcSignal", (ostr) => {
            ostr.writeString(from ?? "");
            ostr.writeString(to ?? "");
            ostr.writeString(type ?? "");
            ostr.writeString(typeof payload === "string" ? payload : JSON.stringify(payload ?? {}));
        });
    }

    async pullRtcSignals(user) {
        await this._ensureConnected();
        return this._invokeStringSeq(this.callPrx, "pullRtcSignals", (ostr) => {
            ostr.writeString(user ?? "");
        });
    }

    async createGroup(name, members) {
        await this._ensureConnected();
        const membersArr = Array.isArray(members) ? members : (members || "").split(",").map(m => m.trim()).filter(Boolean);
        return this._invokeBool(this.chatPrx, "createGroup", (ostr) => {
            ostr.writeString(name ?? "");
            Ice.StringSeqHelper.write(ostr, membersArr);
        });
    }

    async sendAudio(from, to, isGroup, filename, dataBytes) {
        await this._ensureConnected();
        const bytes = dataBytes || new Uint8Array();
        return this._invokeBool(this.chatPrx, "sendAudio", (ostr) => {
            ostr.writeString(from ?? "");
            ostr.writeString(to ?? "");
            ostr.writeBool(!!isGroup);
            ostr.writeString(filename ?? "audio.webm");
            Ice.ByteSeqHelper.write(ostr, bytes);
        });
    }

    // ========== Helpers ==========
    _stringToProxy(identity) {
        const proxyStr = `${identity}:default -h ${this.host} -p ${this.port}`;
        return this.communicator.stringToProxy(proxyStr);
    }

    async _ensureConnected() {
        if (!this.connected) {
            await this.connect();
        }
    }

    async _invokeVoid(prx, operation, marshalFn) {
        // startWriteParams/endWriteParams in Ice already wrap params in an encapsulation,
        // so marshalFn must only write arguments (no extra start/endEncapsulation calls).
        const r = prx.constructor._invoke(
            prx,
            operation,
            Ice.OperationMode.Normal,
            Ice.FormatType.DefaultFormat,
            null,
            marshalFn ?? null,
            null,
            [],
            null
        );
        await r;
        return true;
    }

    async _invokeStringSeq(prx, operation, marshalFn) {
        const r = prx.constructor._invoke(
            prx,
            operation,
            Ice.OperationMode.Normal,
            Ice.FormatType.DefaultFormat,
            null,
            marshalFn ?? null,
            (res) => {
                const istr = res.startReadParams();
                const seq = Ice.StringSeqHelper.read(istr);
                res.endReadParams();
                return seq || [];
            },
            [],
            null
        );
        const result = await r;
        return result || [];
    }

    async _invokeBool(prx, operation, marshalFn) {
        const r = prx.constructor._invoke(
            prx,
            operation,
            Ice.OperationMode.Normal,
            Ice.FormatType.DefaultFormat,
            null,
            marshalFn ?? null,
            (res) => {
                const istr = res.startReadParams();
                const val = istr.readBool();
                res.endReadParams();
                return val;
            },
            [],
            null
        );
        return await r;
    }
}

export default IceClient;
