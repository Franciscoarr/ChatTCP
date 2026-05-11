package cliente;

import servidor.GestorSeguridad;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.util.Base64;

public class ClienteChatSwing extends JFrame {

    // --- Variables de red y control ---
    private Socket socket;
    private BufferedReader entrada; // Para leer datos que llegan del servidor
    private PrintWriter salida;     // Para enviar datos al servidor
    private String nombreUser;
    private String passwordUser;
    private String rolActual;       // Almacena si es ORDINARIO o MODERADOR
    private String salaActual = "#General";
    private boolean conectado = false;

    // --- Variables de la Interfaz Gráfica (GUI) ---
    private JTextArea areaChat;     // Pizarra donde se ven los mensajes
    private JTextField campoMensaje;// Donde el usuario escribe
    private DefaultListModel<String> modeloUsuarios; // Lista dinámica para el panel derecho
    private JLabel labelTituloSala;

    /**
     * Constructor del cliente. Configura la ventana y lanza la autenticación
     */
    public ClienteChatSwing() {
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Cierra el programa al dar a la 'X'
        setLayout(new BorderLayout());

        // Construye la interfaz gráfica y la deja oculta en memoria
        construirInterfaz();
        setLocationRelativeTo(null); // Centra la ventana en la pantalla

        // Lanza el cuadro de diálogo para pedir usuario y contraseña
        iniciarAutenticacion();
    }

    /**
     * Muestra el formulario de Login/Registro
     * Implementa validación de formato y filtrado de código malicioso
     */
    private void iniciarAutenticacion() {
        // Panel con los campos de texto
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField txtNick = new JTextField();
        JPasswordField txtPass = new JPasswordField();
        panel.add(new JLabel("Nickname:"));
        panel.add(txtNick);
        panel.add(new JLabel("Password:"));
        panel.add(txtPass);

        Object[] opciones = {"Iniciar Sesión", "Registrarse", "Cancelar"};
        int result = JOptionPane.showOptionDialog(this, panel, "Acceso al Servidor",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, opciones, opciones[0]);

        // Si el usuario cancela o cierra la ventana, salimos del programa
        if (result == 2 || result == JOptionPane.CLOSED_OPTION) {
            System.exit(0);
        }

        // Obtenemos los textos introducidos
        String nickForm = txtNick.getText().trim();
        String passForm = new String(txtPass.getPassword());
        String accion = (result == 0) ? "LOGIN" : "REGISTER";

        // VALIDACIÓN DE SEGURIDAD 1: Expresión regular (Regex)
        // Obliga a empezar por letra y no permite espacios ni símbolos especiales
        if (!nickForm.matches("^[a-zA-Z][a-zA-Z0-9_-]*$")) {
            JOptionPane.showMessageDialog(this, "Formato inválido. Debe empezar por letra y no contener espacios ni símbolos especiales.", "Alerta de Seguridad", JOptionPane.ERROR_MESSAGE);
            iniciarAutenticacion(); // Recursividad: vuelve a pedir los datos
            return;
        }

        // VALIDACIÓN DE SEGURIDAD 2: Filtrado de URLs para evitar spam o enlaces maliciosos
        nickForm = nickForm.replaceAll("(?i)(http://|https://|www\\.)", "[FILTRADO]");

        this.nombreUser = nickForm;
        this.passwordUser = passForm;

        // Intentamos conectar al servidor con estos datos
        conectarSala(salaActual, accion);
    }

    /**
     * Construye todos los paneles, botones y listas de la ventana principal
     */
    private void construirInterfaz() {
        // --- PANEL IZQUIERDO: SALAS ---
        DefaultListModel<String> modeloSalas = new DefaultListModel<>();
        String[] salas = {"#General", "#Anime", "#Videojuegos", "#Programacion", "#Peliculas", "#Musica", "#Deportes"};
        for (String s : salas) modeloSalas.addElement(s);

        JList<String> listaSalas = new JList<>(modeloSalas);
        listaSalas.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                String s = listaSalas.getSelectedValue();
                if (s != null && !s.equals(salaActual)) cambiarDeSala(s);
            }
        });

        JPanel panelIzq = new JPanel(new BorderLayout());
        panelIzq.setPreferredSize(new Dimension(180, 0));
        panelIzq.add(new JLabel(" CANALES"), BorderLayout.NORTH);
        panelIzq.add(new JScrollPane(listaSalas), BorderLayout.CENTER);

        // --- PANEL CENTRAL: CHAT ---
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        labelTituloSala = new JLabel("Sala: " + salaActual);
        labelTituloSala.setBorder(new EmptyBorder(5, 5, 5, 5));

        campoMensaje = new JTextField();
        JButton btnEnviar = new JButton("Enviar");
        JButton btnFile = new JButton("Archivo");
        JButton btnSalir = new JButton("Salir");

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotones.add(btnFile);
        panelBotones.add(btnEnviar);
        panelBotones.add(btnSalir);

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(panelBotones, BorderLayout.EAST);

        JPanel panelCentral = new JPanel(new BorderLayout());
        panelCentral.add(labelTituloSala, BorderLayout.NORTH);
        panelCentral.add(new JScrollPane(areaChat), BorderLayout.CENTER);
        panelCentral.add(panelInferior, BorderLayout.SOUTH);

        // --- PANEL DERECHO: USUARIOS ---
        modeloUsuarios = new DefaultListModel<>();
        JList<String> listaUsuarios = new JList<>(modeloUsuarios);
        // Doble clic en un usuario prepara el comando /privado automáticamente
        listaUsuarios.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    campoMensaje.setText("/privado " + listaUsuarios.getSelectedValue() + " ");
                }
            }
        });

        JPanel panelDer = new JPanel(new BorderLayout());
        panelDer.setPreferredSize(new Dimension(150, 0));
        panelDer.add(new JLabel("Usuarios"), BorderLayout.NORTH);
        panelDer.add(new JScrollPane(listaUsuarios), BorderLayout.CENTER);

        // Añadimos todo a la ventana principal
        add(panelIzq, BorderLayout.WEST);
        add(panelCentral, BorderLayout.CENTER);
        add(panelDer, BorderLayout.EAST);

        // Asignamos acciones a los botones
        btnEnviar.addActionListener(e -> enviarMensaje());
        campoMensaje.addActionListener(e -> enviarMensaje()); // Funciona al dar Enter
        btnFile.addActionListener(e -> enviarArchivoPrivado(listaUsuarios.getSelectedValue()));

        btnSalir.addActionListener(e -> {
            if (salida != null) salida.println("*****"); // Cierra conexión ordenadamente
            System.exit(0);
        });
    }

    /**
     * Cierra el socket de la sala actual y se reconecta a la nueva
     */
    private void cambiarDeSala(String nuevaSala) {
        try {
            if (salida != null) salida.println("*****");
            conectado = false;
            Thread.sleep(150); // Pausa corta para que el servidor procese la salida
            if (socket != null) socket.close();
        } catch (Exception _) {}

        areaChat.setText("");
        modeloUsuarios.clear();
        salaActual = nuevaSala;
        conectarSala(salaActual, "LOGIN"); // Al cambiar de sala pasamos el LOGIN directo
    }

    /**
     * Inicia un hilo paralelo para conectarse al servidor y escuchar los mensajes
     * sin bloquear o congelar la interfaz gráfica
     */
    private void conectarSala(String sala, String accion) {
        new Thread(() -> {
            try {
                // 1. Establecemos conexión por TCP
                socket = new Socket("localhost", 5000);
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                // 2. Enviamos el protocolo de acceso al servidor: ACCION###NICK###PASS
                salida.println(accion + "###" + nombreUser + "###" + passwordUser);
                salida.println(sala);

                // 3. Esperamos la respuesta del servidor
                String respuestaServer = entrada.readLine();

                // Si hay error en las credenciales
                if (respuestaServer != null && respuestaServer.startsWith("###ERROR-LOGIN###")) {
                    String msgError = respuestaServer.split("###")[2];
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, msgError, "Error de acceso", JOptionPane.ERROR_MESSAGE);
                        // Si la IP está bloqueada, cerramos todo. Si no, le damos otra oportunidad.
                        if (msgError.contains("Bloqueada")) {
                            System.exit(0);
                        } else {
                            iniciarAutenticacion(); // Bucle: Vuelve a sacarle la ventana
                        }
                    });
                    socket.close();
                    return; // Terminamos este hilo
                }
                // Si el servidor acepta las credenciales
                else if (respuestaServer != null && respuestaServer.startsWith("###LOGIN-OK###")) {
                    rolActual = respuestaServer.split("###")[2];
                    conectado = true;

                    // Actualizamos la interfaz gráfica con el éxito de la conexión
                    SwingUtilities.invokeLater(() -> {
                        setTitle("ChatTCP - " + nombreUser + " [" + rolActual + "]");
                        labelTituloSala.setText("Sala: " + sala);
                        if (!modeloUsuarios.contains(nombreUser)) modeloUsuarios.addElement(nombreUser);
                        setVisible(true); // Solo hacemos visible la ventana principal ahora
                    });
                }

                // 4. Bucle infinito escuchando los mensajes que llegan al chat
                String texto;
                while (conectado && (texto = entrada.readLine()) != null) {
                    final String msg = texto;
                    // Mandamos el texto al hilo de la interfaz gráfica para procesarlo
                    SwingUtilities.invokeLater(() -> procesarMensaje(msg));
                }
            } catch (IOException e) {
                if (conectado) SwingUtilities.invokeLater(() -> areaChat.append("Desconectado.\n"));
            }
        }).start(); // Inicia el hilo
    }

    /**
     * Interpreta si el mensaje entrante es texto normal o un comando de control oculto
     */
    private void procesarMensaje(String texto) {
        // Evento: El moderador te ha expulsado
        if (texto.equals("###KICKED###")) {
            JOptionPane.showMessageDialog(this, "Has sido expulsado del canal por un moderador.", "Expulsado", JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }

        // Evento: Alguien ha borrado su mensaje (/delmsg)
        if (texto.startsWith("###DEL-LAST###")) {
            String targetNick = texto.substring("###DEL-LAST###".length()).trim();
            String currentText = areaChat.getText();
            String[] lines = currentText.split("\n");
            StringBuilder newText = new StringBuilder();
            boolean deleted = false;

            // Recorremos el chat de abajo a arriba para borrar el ÚLTIMO mensaje de esa persona
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!deleted && (lines[i].startsWith(targetNick + "> ") ||
                        lines[i].startsWith("[PRIVADO de " + targetNick + "]:") ||
                        lines[i].startsWith("[ARCHIVO PRIVADO de " + targetNick + "]:") ||
                        lines[i].startsWith("[Enviado a "))) {

                    newText.insert(0, ">> Mensaje eliminado <<\n");
                    deleted = true;
                } else {
                    newText.insert(0, lines[i] + "\n");
                }
            }
            areaChat.setText(newText.toString());
            return;
        }

        // Evento: Actualizar lista de la derecha (Alguien entra)
        if (texto.startsWith("###PARSER-ENTRA###")) {
            String nick = texto.substring(18).trim();
            if (!modeloUsuarios.contains(nick)) modeloUsuarios.addElement(nick);
            return;
        }

        // Evento: Actualizar lista de la derecha (Alguien sale)
        if (texto.startsWith("###PARSER-SALE###")) {
            modeloUsuarios.removeElement(texto.substring(17).trim());
            return;
        }

        // Evento: Desencriptar mensaje/archivo privado mediante AES
        if (texto.contains("PRIVADO de") || texto.contains("Enviado a")) {
            String[] partes = texto.split("]: ", 2);
            if (partes.length == 2) {
                String header = partes[0] + "]: ";
                String descifrado = GestorSeguridad.descifrarAES(partes[1]);
                areaChat.append(header + descifrado + "\n");
                return;
            }
        }

        // Si no era ningún comando oculto, simplemente lo pintamos en la pizarra
        areaChat.append(texto + "\n");
        // Hacemos autoscroll hacia abajo
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    /**
     * Envía lo que el usuario haya escrito en la caja de texto
     */
    private void enviarMensaje() {
        String msg = campoMensaje.getText();
        if (msg.isEmpty()) return;

        // Si es un mensaje privado, aplicamos cifrado AES al contenido antes de que viaje por la red
        if (msg.startsWith("/privado ")) {
            String[] partes = msg.split(" ", 3);
            if (partes.length == 3) {
                String cifrado = GestorSeguridad.cifrarAES(partes[2]);
                salida.println("/privado " + partes[1] + " " + cifrado);
            }
        } else {
            // Envío de texto normal al canal
            salida.println(msg);
        }
        campoMensaje.setText(""); // Limpia la caja
    }

    /**
     * Lógica para enviar un archivo cifrado a otro usuario
     */
    private void enviarArchivoPrivado(String destino) {
        if (destino == null || destino.equals(nombreUser)) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la lista para enviarle el archivo.");
            return;
        }
        JFileChooser fc = new JFileChooser(); // Abre el explorador de archivos de Windows/Mac
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                // Leemos el archivo físico y lo convertimos a un array de bytes
                byte[] bytes = java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath());
                // Codificamos los bytes a String mediante Base64
                String base64 = Base64.getEncoder().encodeToString(bytes);
                // Ciframos la cadena resultante con AES por seguridad
                String archivoCifrado = GestorSeguridad.cifrarAES("[ARCHIVO: " + fc.getSelectedFile().getName() + "] Payload: " + base64.substring(0, Math.min(base64.length(), 20)) + "... (truncado)");

                // Enviamos el comando de archivo al servidor
                salida.println("/file " + destino + " " + archivoCifrado);
                JOptionPane.showMessageDialog(this, "Archivo enviado cifrado a " + destino);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClienteChatSwing::new);
    }
}