package cliente;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;

/**
 * Clase que gestiona la interfaz gráfica y la lógica de red del cliente.
 * Implementa los requisitos de Nickname único, multicanal
 * y entorno gráfico
 */
public class ClienteChatSwing extends JFrame {

    // --- Atributos de Red ---
    private Socket socket;
    private BufferedReader entrada; // Para leer lo que envía el servidor
    private PrintWriter salida;     // Para enviar mensajes al servidor
    private String nombreUser;
    private String salaActual = "#General";
    private boolean conectado = false;

    // --- Componentes GUI ---
    private JTextArea areaChat;
    private JTextField campoMensaje;
    private DefaultListModel<String> modeloUsuarios; // Lista dinámica de usuarios
    private JLabel labelTituloSala;

    public ClienteChatSwing() {
        // 1. Pedimos el Nick nada más arrancar
        pedirNickInicial();

        // Configuración básica de la ventana (JFrame)
        setTitle("ChatTCP - " + nombreUser);
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 2. Construimos la interfaz y conectamos al servidor
        construirInterfaz();
        conectarSala(salaActual);

        setVisible(true);
    }

    /**
     * Metodo para solicitar el Nickname. Si se deja vacío, genera uno aleatorio.
     */
    private void pedirNickInicial() {
        nombreUser = JOptionPane.showInputDialog(this, "Introduce tu Nick:", "Entrada al Chat", JOptionPane.QUESTION_MESSAGE);
        if (nombreUser == null || nombreUser.trim().isEmpty()) {
            nombreUser = "Invitado" + (int)(Math.random() * 1000);
        }
    }

    private void construirInterfaz() {
        // --- PANEL IZQUIERDO: SELECCIÓN DE CANALES ---
        DefaultListModel<String> modeloSalas = new DefaultListModel<>();
        String[] salas = {"#General", "#Anime", "#Videojuegos", "#Películas", "#Programacion"};
        for (String s : salas) modeloSalas.addElement(s);

        JList<String> listaSalas = new JList<>(modeloSalas);
        listaSalas.setBackground(new Color(230, 240, 255));
        listaSalas.setFixedCellHeight(30);

        // Listener para cambiar de sala al hacer clic
        listaSalas.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                String salaSeleccionada = listaSalas.getSelectedValue();
                if (salaSeleccionada != null && !salaSeleccionada.equals(salaActual)) {
                    cambiarDeSala(salaSeleccionada);
                }
            }
        });

        JPanel panelIzq = new JPanel(new BorderLayout());
        panelIzq.setBackground(new Color(50, 80, 160));
        panelIzq.setPreferredSize(new Dimension(180, 0));
        JLabel tituloSalas = new JLabel(" CANALES", SwingConstants.CENTER);
        tituloSalas.setForeground(Color.WHITE);
        panelIzq.add(tituloSalas, BorderLayout.NORTH);
        panelIzq.add(new JScrollPane(listaSalas), BorderLayout.CENTER);

        // --- PANEL CENTRAL: ÁREA DE CHAT Y ESCRITURA ---
        areaChat = new JTextArea();
        areaChat.setEditable(false); // El usuario no escribe directamente en el chat
        labelTituloSala = new JLabel("Sala: " + salaActual);
        labelTituloSala.setFont(new Font("Arial", Font.BOLD, 16));
        labelTituloSala.setBorder(new EmptyBorder(5, 5, 5, 5));

        campoMensaje = new JTextField();
        JButton btnEnviar = new JButton("Enviar");
        JButton btnSalir = new JButton("Salir"); // Requisito [cite: 33]
        btnSalir.setBackground(new Color(220, 50, 50));
        btnSalir.setForeground(Color.WHITE);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotones.add(btnEnviar);
        panelBotones.add(btnSalir);

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(panelBotones, BorderLayout.EAST);

        JPanel panelCentral = new JPanel(new BorderLayout());
        panelCentral.add(labelTituloSala, BorderLayout.NORTH);
        panelCentral.add(new JScrollPane(areaChat), BorderLayout.CENTER);
        panelCentral.add(panelInferior, BorderLayout.SOUTH);

        // --- PANEL DERECHO: LISTA DE USUARIOS ACTIVOS ---
        modeloUsuarios = new DefaultListModel<>();
        JList<String> listaUsuarios = new JList<>(modeloUsuarios);

        // Doble clic en un nombre para preparar un Mensaje Privado
        listaUsuarios.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String userSel = listaUsuarios.getSelectedValue();
                    if (userSel != null) {
                        campoMensaje.setText("/privado " + userSel + " ");
                        campoMensaje.requestFocus();
                    }
                }
            }
        });

        JPanel panelDer = new JPanel(new BorderLayout());
        panelDer.setPreferredSize(new Dimension(150, 0));
        panelDer.add(new JLabel("Usuarios", SwingConstants.CENTER), BorderLayout.NORTH);
        panelDer.add(new JScrollPane(listaUsuarios), BorderLayout.CENTER);

        // Añadimos todo al frame principal
        add(panelIzq, BorderLayout.WEST);
        add(panelCentral, BorderLayout.CENTER);
        add(panelDer, BorderLayout.EAST);

        // Listeners de acción
        btnEnviar.addActionListener(e -> enviarMensaje());
        campoMensaje.addActionListener(e -> enviarMensaje());
        btnSalir.addActionListener(e -> {
            if (salida != null) salida.println("*****"); // Notifica cierre al servidor
            System.exit(0);
        });
    }

    /**
     * Gestiona la desconexión de la sala actual y conexión a la nueva
     */
    private void cambiarDeSala(String nuevaSala) {
        try {
            if (salida != null) salida.println("*****");
            conectado = false;
            Thread.sleep(150); // Pausa para que el servidor procese la salida antes de la nueva entrada
            if (socket != null) socket.close();
        } catch (Exception _) {}

        areaChat.setText("");
        modeloUsuarios.clear();
        salaActual = nuevaSala;
        labelTituloSala.setText("Conectando a " + salaActual + "...");
        conectarSala(salaActual);
    }

    /**
     * Hilo secundario para no bloquear la interfaz gráfica mientras se espera respuesta del servidor
     */
    private void conectarSala(String sala) {
        new Thread(() -> {
            try {
                // Conexión TCP al puerto 5000
                socket = new Socket("localhost", 5000);
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                // Protocolo de inicio: Enviamos Nick y luego Sala
                salida.println(nombreUser);
                salida.println(sala);

                conectado = true;

                // Nos añadimos a nuestra propia lista local
                SwingUtilities.invokeLater(() -> {
                    labelTituloSala.setText("Sala: " + sala);
                    if (!modeloUsuarios.contains(nombreUser)) modeloUsuarios.addElement(nombreUser);
                });

                // Bucle de lectura constante
                String texto;
                while (conectado && (texto = entrada.readLine()) != null) {
                    final String msg = texto;
                    SwingUtilities.invokeLater(() -> procesarMensaje(msg));
                }
            } catch (IOException e) {
                if (conectado) SwingUtilities.invokeLater(() -> areaChat.append("Error: Servidor desconectado.\n"));
            }
        }).start();
    }

    /**
     * Interpreta si el mensaje del servidor es texto para el chat o un comando de control
     */
    private void procesarMensaje(String texto) {
        // CASO: Nick ya está en uso
        if (texto.equals("###ERROR-NICK###")) {
            conectado = false;
            String nuevoNick = JOptionPane.showInputDialog(this,
                    "El nick '" + nombreUser + "' ya está ocupado. Elige otro:",
                    "Nick Duplicado", JOptionPane.WARNING_MESSAGE);

            if (nuevoNick == null || nuevoNick.trim().isEmpty()) System.exit(0);
            else {
                nombreUser = nuevoNick;
                setTitle("ChatTCP - " + nombreUser);
                conectarSala(salaActual);
            }
            return;
        }

        // Comando oculto para actualizar la lista de usuarios a la derecha
        if (texto.startsWith("###PARSER-ENTRA###")) {
            String nick = texto.substring(18).trim();
            if (!modeloUsuarios.contains(nick)) modeloUsuarios.addElement(nick);
            return;
        }

        if (texto.startsWith("###PARSER-SALE###")) {
            String nick = texto.substring(17).trim();
            modeloUsuarios.removeElement(nick);
            return;
        }

        // Mensaje normal de chat
        areaChat.append(texto + "\n");
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    private void enviarMensaje() {
        if (campoMensaje.getText().isEmpty()) return;
        if (salida != null) {
            salida.println(campoMensaje.getText());
            campoMensaje.setText("");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClienteChatSwing::new);
    }
}