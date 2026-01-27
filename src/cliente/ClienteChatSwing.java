package cliente;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;

public class ClienteChatSwing extends JFrame {

    //Red
    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombreUser;
    private String salaActual = "#General"; //Sala por defecto
    private boolean conectado = false;

    //GUI
    private JTextArea areaChat;
    private JTextField campoMensaje;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloUsuarios;
    private JLabel labelTituloSala;

    public ClienteChatSwing() {
        nombreUser = JOptionPane.showInputDialog(this, "Introduce tu Nick:", "Entrada", JOptionPane.QUESTION_MESSAGE);
        if (nombreUser == null || nombreUser.trim().isEmpty()) nombreUser = "Invitado" + (int)(Math.random()*100);

        setTitle("ChatTCP - " + nombreUser);
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        construirInterfaz();

        //Conectar inicialmente a la sala por defecto
        conectarSala(salaActual);

        setVisible(true);
    }

    private void construirInterfaz() {
        //Panel izquierdo
        DefaultListModel<String> modeloSalas = new DefaultListModel<>();
        modeloSalas.addElement("#General");
        modeloSalas.addElement("#Anime");
        modeloSalas.addElement("#Videojuegos");
        modeloSalas.addElement("#Películas");
        modeloSalas.addElement("#Programacion");

        JList<String> listaSalas = new JList<>(modeloSalas);
        listaSalas.setBackground(new Color(230, 240, 255));
        listaSalas.setFixedCellHeight(30);

        //Click en una sala
        listaSalas.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                String salaSeleccionada = listaSalas.getSelectedValue();
                if (salaSeleccionada != null && !salaSeleccionada.equals(salaActual)) {
                    //Cambiar de sala
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

        //Panel centro
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        labelTituloSala = new JLabel("Sala: " + salaActual);
        labelTituloSala.setFont(new Font("Arial", Font.BOLD, 16));
        labelTituloSala.setBorder(new EmptyBorder(5,5,5,5));

        campoMensaje = new JTextField();
        JButton btnEnviar = new JButton("Enviar");
        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(btnEnviar, BorderLayout.EAST);

        JPanel panelCentral = new JPanel(new BorderLayout());
        panelCentral.add(labelTituloSala, BorderLayout.NORTH);
        panelCentral.add(new JScrollPane(areaChat), BorderLayout.CENTER);
        panelCentral.add(panelInferior, BorderLayout.SOUTH);

        //Panel derecho
        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        JPanel panelDer = new JPanel(new BorderLayout());
        panelDer.setPreferredSize(new Dimension(150, 0));
        panelDer.add(new JLabel("Usuarios", SwingConstants.CENTER), BorderLayout.NORTH);
        panelDer.add(new JScrollPane(listaUsuarios), BorderLayout.CENTER);

        add(panelIzq, BorderLayout.WEST);
        add(panelCentral, BorderLayout.CENTER);
        add(panelDer, BorderLayout.EAST);

        //Listeners Enviar
        btnEnviar.addActionListener(e -> enviarMensaje());
        campoMensaje.addActionListener(e -> enviarMensaje());
    }

    //Conexión y cambio de salas

    private void cambiarDeSala(String nuevaSala) {
        //Desconectar de la actual
        try {
            if (salida != null) salida.println("*****"); // Avisar al server
            conectado = false;
            if (socket != null) socket.close();
        } catch (Exception e) {}

        //Limpiar pantalla
        areaChat.setText("");
        modeloUsuarios.clear();

        //Conectar a la nueva
        salaActual = nuevaSala;
        labelTituloSala.setText("Conectando a " + salaActual + "...");
        conectarSala(salaActual);
    }

    private void conectarSala(String sala) {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 5000);
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                //PROTOCOLO: 1.- Nombre, 2.- Sala
                salida.println(nombreUser);
                salida.println(sala);

                conectado = true;
                SwingUtilities.invokeLater(() -> labelTituloSala.setText("Sala: " + sala));

                String texto;
                while (conectado && (texto = entrada.readLine()) != null) {
                    String finalTexto = texto;
                    SwingUtilities.invokeLater(() -> procesarMensaje(finalTexto));
                }
            } catch (IOException e) {
            }
        }).start();
    }

    private void procesarMensaje(String texto) {

        // Si es comando de ENTRADA
        if (texto.startsWith("###PARSER-ENTRA###")) {
            String nombreNuevo = texto.substring("###PARSER-ENTRA###".length()).trim();
            // Añadir a la lista visual si no existe
            if (!modeloUsuarios.contains(nombreNuevo) && !nombreNuevo.isEmpty()) {
                modeloUsuarios.addElement(nombreNuevo);
            }
            return;
        }

        // Si es comando de SALIDA
        if (texto.startsWith("###PARSER-SALE###")) {
            String nombreSale = texto.substring("###PARSER-SALE###".length()).trim();
            modeloUsuarios.removeElement(nombreSale);
            return;
        }

        // Si el código llega hasta aquí, significa que NO era un comando oculto,
        // así que es un mensaje de chat normal y corriente. Lo escribimos UNA SOLA VEZ.
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