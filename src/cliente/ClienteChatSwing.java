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

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombreUser;
    private String passwordUser;
    private String rolActual;
    private String salaActual = "#General";
    private boolean conectado = false;

    private JTextArea areaChat;
    private JTextField campoMensaje;
    private DefaultListModel<String> modeloUsuarios;
    private JLabel labelTituloSala;

    public ClienteChatSwing() {
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Se construye todo oculto primero
        construirInterfaz();
        setLocationRelativeTo(null);

        // Pide los datos. El formulario se encarga de mostrar la GUI si tiene éxito
        iniciarAutenticacion();
    }

    /**
     * Requisito: Formulario de Login/Registro recursivo.
     * Te vuelve a saltar si te equivocas (hasta límite IP del servidor).
     */
    private void iniciarAutenticacion() {
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

        // Si pulsa Cancelar o cierra la ventana
        if (result == 2 || result == JOptionPane.CLOSED_OPTION) {
            System.exit(0);
        }

        String nickForm = txtNick.getText().trim();
        String passForm = new String(txtPass.getPassword());
        String accion = (result == 0) ? "LOGIN" : "REGISTER";

        if (!nickForm.matches("^[a-zA-Z][a-zA-Z0-9_-]*$")) {
            JOptionPane.showMessageDialog(this, "Formato inválido. Debe empezar por letra y no contener espacios ni símbolos especiales.", "Alerta de Seguridad", JOptionPane.ERROR_MESSAGE);
            iniciarAutenticacion(); // Repetimos bucle
            return;
        }

        nickForm = nickForm.replaceAll("(?i)(http://|https://|www\\.)", "[FILTRADO]");

        this.nombreUser = nickForm;
        this.passwordUser = passForm;

        conectarSala(salaActual, accion);
    }

    private void construirInterfaz() {
        DefaultListModel<String> modeloSalas = new DefaultListModel<>();
        String[] salas = {"#General", "#Anime"};
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

        modeloUsuarios = new DefaultListModel<>();
        JList<String> listaUsuarios = new JList<>(modeloUsuarios);
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

        add(panelIzq, BorderLayout.WEST);
        add(panelCentral, BorderLayout.CENTER);
        add(panelDer, BorderLayout.EAST);

        btnEnviar.addActionListener(e -> enviarMensaje());
        campoMensaje.addActionListener(e -> enviarMensaje());
        btnFile.addActionListener(e -> enviarArchivoPrivado(listaUsuarios.getSelectedValue()));

        btnSalir.addActionListener(e -> {
            if (salida != null) salida.println("*****");
            System.exit(0);
        });
    }

    private void cambiarDeSala(String nuevaSala) {
        try {
            if (salida != null) salida.println("*****");
            conectado = false;
            Thread.sleep(150);
            if (socket != null) socket.close();
        } catch (Exception _) {}

        areaChat.setText("");
        modeloUsuarios.clear();
        salaActual = nuevaSala;
        conectarSala(salaActual, "LOGIN"); // Al cambiar de sala pasamos el LOGIN directo
    }

    private void conectarSala(String sala, String accion) {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 5000);
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                // Protocolo: ACCION###NICK###PASS
                salida.println(accion + "###" + nombreUser + "###" + passwordUser);
                salida.println(sala);

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
                else if (respuestaServer != null && respuestaServer.startsWith("###LOGIN-OK###")) {
                    rolActual = respuestaServer.split("###")[2];
                    conectado = true;

                    SwingUtilities.invokeLater(() -> {
                        setTitle("ChatTCP - " + nombreUser + " [" + rolActual + "]");
                        labelTituloSala.setText("Sala: " + sala);
                        if (!modeloUsuarios.contains(nombreUser)) modeloUsuarios.addElement(nombreUser);
                        setVisible(true); // Solo hacemos visible el chat si ha tenido exito
                    });
                }

                // Hilo escuchando al chat
                String texto;
                while (conectado && (texto = entrada.readLine()) != null) {
                    final String msg = texto;
                    SwingUtilities.invokeLater(() -> procesarMensaje(msg));
                }
            } catch (IOException e) {
                if (conectado) SwingUtilities.invokeLater(() -> areaChat.append("Desconectado.\n"));
            }
        }).start();
    }

    private void procesarMensaje(String texto) {
        if (texto.equals("###KICKED###")) {
            JOptionPane.showMessageDialog(this, "Has sido expulsado del canal por un moderador.", "Expulsado", JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }

        if (texto.startsWith("###DEL-LAST###")) {
            String targetNick = texto.substring("###DEL-LAST###".length()).trim();
            String currentText = areaChat.getText();
            String[] lines = currentText.split("\n");
            StringBuilder newText = new StringBuilder();
            boolean deleted = false;

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

        if (texto.startsWith("###PARSER-ENTRA###")) {
            String nick = texto.substring(18).trim();
            if (!modeloUsuarios.contains(nick)) modeloUsuarios.addElement(nick);
            return;
        }
        if (texto.startsWith("###PARSER-SALE###")) {
            modeloUsuarios.removeElement(texto.substring(17).trim());
            return;
        }

        if (texto.contains("PRIVADO de") || texto.contains("Enviado a")) {
            String[] partes = texto.split("]: ", 2);
            if (partes.length == 2) {
                String header = partes[0] + "]: ";
                String descifrado = GestorSeguridad.descifrarAES(partes[1]);
                areaChat.append(header + descifrado + "\n");
                return;
            }
        }

        areaChat.append(texto + "\n");
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    private void enviarMensaje() {
        String msg = campoMensaje.getText();
        if (msg.isEmpty()) return;

        if (msg.startsWith("/privado ")) {
            String[] partes = msg.split(" ", 3);
            if (partes.length == 3) {
                String cifrado = GestorSeguridad.cifrarAES(partes[2]);
                salida.println("/privado " + partes[1] + " " + cifrado);
            }
        } else {
            salida.println(msg);
        }
        campoMensaje.setText("");
    }

    private void enviarArchivoPrivado(String destino) {
        if (destino == null || destino.equals(nombreUser)) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la lista para enviarle el archivo.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String archivoCifrado = GestorSeguridad.cifrarAES("[ARCHIVO: " + fc.getSelectedFile().getName() + "] Payload: " + base64.substring(0, Math.min(base64.length(), 20)) + "... (truncado)");
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