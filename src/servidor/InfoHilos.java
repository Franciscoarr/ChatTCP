package servidor;

import java.net.Socket;

public class InfoHilos {
    private int conexiones; //Número de conexiones
    private int actuales;   //Conectados ahora mismo
    private int maximo;     //Maximo permitido
    private Socket[] tabla; //Array de sockets
    private String mensajes; //Historial del chat

    public InfoHilos(int maximo) {
        this.maximo = maximo;
        this.conexiones = 0;
        this.actuales = 0;
        this.tabla = new Socket[maximo];
        this.mensajes = "";
    }

    public synchronized int getConexiones() {
        return conexiones;

    }
    public synchronized void setConexiones(int conexiones) {
        this.conexiones = conexiones;
    }

    public synchronized int getActuales() {
        return actuales;
    }
    public synchronized void setActuales(int actuales) {
        this.actuales = actuales;
    }

    public synchronized void addSocket(Socket s, int pos) {
        if (pos < maximo) {
            tabla[pos] = s;
        }
    }

    public synchronized Socket[] getTabla() {
        return tabla;
    }

    public synchronized String getMensajes() {
        return mensajes;
    }
    public synchronized void setMensajes(String mensajes) {
        this.mensajes = mensajes;
    }
}