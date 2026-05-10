package servidor;

import java.net.Socket;

public class InfoHilos {
    private int conexiones;
    private int actuales;
    private final int maximo;
    private final Socket[] tabla;
    private String mensajes;
    private boolean suspendido; // Nueva propiedad para moderación

    public InfoHilos(int maximo) {
        this.maximo = maximo;
        this.conexiones = 0;
        this.actuales = 0;
        this.tabla = new Socket[maximo];
        this.mensajes = "";
        this.suspendido = false;
    }

    public synchronized boolean isSuspendido() { return suspendido; }
    public synchronized void setSuspendido(boolean suspendido) { this.suspendido = suspendido; }

    public synchronized int getConexiones() { return conexiones; }
    public synchronized int getActuales() { return actuales; }
    public synchronized void setActuales(int actuales) { this.actuales = actuales; }
    public synchronized String getMensajes() { return mensajes; }
    public synchronized void setMensajes(String mensajes) { this.mensajes = mensajes; }
    public synchronized Socket[] getTabla() { return tabla; }

    public synchronized boolean addSocket(Socket s) {
        for (int i = 0; i < maximo; i++) {
            if (tabla[i] == null || tabla[i].isClosed()) {
                tabla[i] = s;
                this.conexiones++;
                return true;
            }
        }
        return false;
    }
}