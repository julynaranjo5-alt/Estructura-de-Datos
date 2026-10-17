import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;
import java.util.Scanner;

public class SimuladorPlagasAgro {

   
    private int[][] grillaInfestacion;
    private static final int FILAS = 8;
    private static final int COLS = 8;

    private Point[] focosIniciales;
    private ListaCircularMigracion listaMigracion;
    private ListaDobleResistencia listaResistencia;
    private ListaSimpleTratamientos listaTratamientos;
    private Stack<int[][]> pilaSnapshots;
    private Queue<Point> colaCuarentena;


    private JFrame ventana;
    private JPanel panelGrilla;
    private JButton[][] botonesGrilla;
    private JTextArea areaLog;
    private JLabel lblEstadoCuarentena;

    public SimuladorPlagasAgro() {
        inicializarEstructuras();
        
        
        if (!GraphicsEnvironment.isHeadless()) {
            construirGUI();
        } else {
            System.out.println("=================================================");
            System.out.println("Modo Interactivo por Consola (Entorno sin pantalla)");
            System.out.println("=================================================\n");
            iniciarModoConsolaInteractivo();
        }
    }

    private void inicializarEstructuras() {
        grillaInfestacion = new int[FILAS][COLS];
        pilaSnapshots = new Stack<>();
        colaCuarentena = new LinkedList<>();


        focosIniciales = new Point[] {
            new Point(1, 1),
            new Point(4, 4),
            new Point(6, 2)
        };

        for (Point foco : focosIniciales) {
            grillaInfestacion[foco.x][foco.y] = 80; 
        }

   
        listaMigracion = new ListaCircularMigracion();
        listaMigracion.insertar("Broca del Café (Hypothenemus hampei)");
        listaMigracion.insertar("Roya Amarilla (Hemileia vastatrix)");
        listaMigracion.insertar("Gusano Cogollero (Spodoptera frugiperda)");


        listaResistencia = new ListaDobleResistencia();
        for (int i = 0; i < FILAS; i++) {
            listaResistencia.insertarAlFinal(0.12 + (i * 0.04));
        }

 
        listaTratamientos = new ListaSimpleTratamientos();
    }

    private void construirGUI() {
        ventana = new JFrame("AgTech - Simulador de Propagación de Plagas");
        ventana.setSize(1024, 700);
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.setLocationRelativeTo(null);
        ventana.setLayout(new BorderLayout(10, 10));

        JPanel panelControl = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnSiguientePaso = new JButton("Simular Siguiente Ciclo");
        JButton btnAplicarTratamiento = new JButton("Aplicar Tratamiento (Cola)");
        JButton btnDeshacer = new JButton("Deshacer Paso (Pila Undo)");

        btnSiguientePaso.setBackground(new Color(220, 245, 220));
        btnAplicarTratamiento.setBackground(new Color(220, 230, 245));
        btnDeshacer.setBackground(new Color(255, 230, 230));

        btnSiguientePaso.addActionListener(e -> simularPasoPropagacion());
        btnAplicarTratamiento.addActionListener(e -> aplicarTratamientoCuarentena());
        btnDeshacer.addActionListener(e -> deshacerPaso());

        panelControl.add(btnSiguientePaso);
        panelControl.add(btnAplicarTratamiento);
        panelControl.add(btnDeshacer);
        ventana.add(panelControl, BorderLayout.NORTH);

        panelGrilla = new JPanel(new GridLayout(FILAS, COLS, 4, 4));
        botonesGrilla = new JButton[FILAS][COLS];

        for (int i = 0; i < FILAS; i++) {
            for (int j = 0; j < COLS; j++) {
                botonesGrilla[i][j] = new JButton();
                botonesGrilla[i][j].setOpaque(true);
                botonesGrilla[i][j].setFont(new Font("SansSerif", Font.BOLD, 12));
                panelGrilla.add(botonesGrilla[i][j]);
            }
        }
        ventana.add(panelGrilla, BorderLayout.CENTER);

        JPanel panelLateral = new JPanel(new BorderLayout(5, 5));
        panelLateral.setPreferredSize(new Dimension(340, 0));

        areaLog = new JTextArea();
        areaLog.setEditable(false);
        areaLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollLog = new JScrollPane(areaLog);

        lblEstadoCuarentena = new JLabel("Parcelas en Cuarentena (Cola): 0");
        lblEstadoCuarentena.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblEstadoCuarentena.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        panelLateral.add(lblEstadoCuarentena, BorderLayout.NORTH);
        panelLateral.add(scrollLog, BorderLayout.CENTER);
        ventana.add(panelLateral, BorderLayout.EAST);

        actualizarVistaGrilla();
        log("Sistema AgTech iniciado en entorno gráfico.");
        ventana.setVisible(true);
    }

    private void guardarSnapshot() {
        int[][] copia = new int[FILAS][COLS];
        for (int i = 0; i < FILAS; i++) {
            System.arraycopy(grillaInfestacion[i], 0, copia[i], 0, COLS);
        }
        pilaSnapshots.push(copia); 
    }

    public void simularPasoPropagacion() {
        guardarSnapshot();

        int[][] nuevaGrilla = new int[FILAS][COLS];
        String plagaActual = listaMigracion.obtenerSiguiente();

        for (int i = 0; i < FILAS; i++) {
            double resistenciaFila = listaResistencia.obtenerResistencia(i);

            for (int j = 0; j < COLS; j++) {
                int infestacionActual = grillaInfestacion[i][j];
                nuevaGrilla[i][j] = Math.max(nuevaGrilla[i][j], infestacionActual);

                if (infestacionActual > 0) {
                    int[][] vecinos = {{-1,0}, {1,0}, {0,-1}, {0,1}, {-1,-1}, {-1,1}, {1,-1}, {1,1}};
                    for (int[] v : vecinos) {
                        int ni = i + v[0];
                        int nj = j + v[1];

                        if (ni >= 0 && ni < FILAS && nj >= 0 && nj < COLS) {
                            int incremento = (int) (12 * (1.0 - resistenciaFila));
                            nuevaGrilla[ni][nj] = Math.min(100, nuevaGrilla[ni][nj] + incremento);
                        }
                    }
                }
            }
        }

        grillaInfestacion = nuevaGrilla;

     
        for (int i = 0; i < FILAS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (grillaInfestacion[i][j] >= 75) {
                    Point p = new Point(i, j);
                    if (!colaCuarentena.contains(p)) {
                        colaCuarentena.add(p);
                    }
                }
            }
        }

        log("Ciclo simulado. Plaga activa: " + plagaActual);
        if (!GraphicsEnvironment.isHeadless() && ventana != null) {
            actualizarVistaGrilla();
        }
    }

    public void aplicarTratamientoCuarentena() {
        if (colaCuarentena.isEmpty()) {
            log("No hay parcelas críticas en la cola de cuarentena.");
            return;
        }

        guardarSnapshot();
        Point parcela = colaCuarentena.poll(); 
        grillaInfestacion[parcela.x][parcela.y] = 10;

        String detalle = "Fumigación en celda (" + parcela.x + ", " + parcela.y + ")";
        listaTratamientos.insertar(detalle); 

        log("Tratamiento aplicado en celda (" + parcela.x + ", " + parcela.y + ")");
        if (!GraphicsEnvironment.isHeadless() && ventana != null) {
            actualizarVistaGrilla();
        }
    }

    public void deshacerPaso() {
        if (!pilaSnapshots.isEmpty()) {
            grillaInfestacion = pilaSnapshots.pop(); 
            
            log("Acción deshecha. Estado restaurado desde la Pila.");
            if (!GraphicsEnvironment.isHeadless() && ventana != null) {
                actualizarVistaGrilla();
            }
        } else {
            log("Advertencia: No hay estados previos en la pila.");
        }
    }

    private void actualizarVistaGrilla() {
        if (botonesGrilla == null) return;
        for (int i = 0; i < FILAS; i++) {
            for (int j = 0; j < COLS; j++) {
                int val = grillaInfestacion[i][j];
                botonesGrilla[i][j].setText(val + "%");

                if (val == 0) {
                    botonesGrilla[i][j].setBackground(new Color(190, 240, 190));
                } else if (val < 50) {
                    botonesGrilla[i][j].setBackground(new Color(255, 240, 160));
                } else if (val < 75) {
                    botonesGrilla[i][j].setBackground(new Color(255, 180, 110));
                } else {
                    botonesGrilla[i][j].setBackground(new Color(255, 110, 110));
                }
            }
        }
        if (lblEstadoCuarentena != null) {
            lblEstadoCuarentena.setText("Parcelas en Cuarentena (Cola): " + colaCuarentena.size());
        }
    }

    public void imprimirGrillaConsola() {
        System.out.println("--- MAPA DE INFESTACIÓN DE CULTIVO (MATRIZ 2D) ---");
        for (int i = 0; i < FILAS; i++) {
            for (int j = 0; j < COLS; j++) {
                System.out.printf("[%3d%%]", grillaInfestacion[i][j]);
            }
            System.out.println();
        }
        System.out.println("Parcelas en Cuarentena (Cola): " + colaCuarentena.size() + "\n");
    }

    private void log(String mensaje) {
        if (areaLog != null) {
            areaLog.append("• " + mensaje + "\n");
        }
        System.out.println("[LOG] " + mensaje);
    }

    private void iniciarModoConsolaInteractivo() {
        Scanner scanner = new Scanner(System.in);
        int opcion = -1;

        while (opcion != 0) {
            imprimirGrillaConsola();
            System.out.println("=== MENÚ INTERACTIVO AGTECH ===");
            System.out.println("1. Simular Siguiente Ciclo de Propagación");
            System.out.println("2. Aplicar Tratamiento en Cuarentena (Cola FIFO)");
            System.out.println("3. Deshacer Paso (Pila LIFO Undo)");
            System.out.println("0. Salir");
            System.out.print("Seleccione una opción: ");

            if (scanner.hasNextInt()) {
                opcion = scanner.nextInt();
                switch (opcion) {
                    case 1:
                        simularPasoPropagacion();
                        break;
                    case 2:
                        aplicarTratamientoCuarentena();
                        break;
                    case 3:
                        deshacerPaso();
                        break;
                    case 0:
                        System.out.println("Saliendo del simulador. ¡Hasta luego!");
                        break;
                    default:
                        System.out.println("Opción no válida. Intente de nuevo.");
                }
            } else {
                break;
            }
        }
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            new SimuladorPlagasAgro(); 
        } else {
            SwingUtilities.invokeLater(SimuladorPlagasAgro::new);
        }
    }

   

    class ListaCircularMigracion {
        class Nodo {
            String plaga;
            Nodo siguiente;
            Nodo(String p) { this.plaga = p; }
        }
        private Nodo cabeza = null, cola = null, actual = null;

        public void insertar(String plaga) {
            Nodo nuevo = new Nodo(plaga);
            if (cabeza == null) {
                cabeza = cola = nuevo;
                nuevo.siguiente = cabeza;
            } else {
                cola.siguiente = nuevo;
                cola = nuevo;
                cola.siguiente = cabeza;
            }
            actual = cabeza;
        }

        public String obtenerSiguiente() {
            if (actual == null) return "N/A";
            String nombre = actual.plaga;
            actual = actual.siguiente;
            return nombre;
        }
    }

    class ListaDobleResistencia {
        class Nodo {
            double resistencia;
            Nodo previo, siguiente;
            Nodo(double r) { this.resistencia = r; }
        }
        private Nodo cabeza = null, cola = null;

        public void insertarAlFinal(double r) {
            Nodo nuevo = new Nodo(r);
            if (cabeza == null) {
                cabeza = cola = nuevo;
            } else {
                cola.siguiente = nuevo;
                nuevo.previo = cola;
                cola = nuevo;
            }
        }

        public double obtenerResistencia(int indice) {
            Nodo temp = cabeza;
            int i = 0;
            while (temp != null && i < indice) {
                temp = temp.siguiente;
                i++;
            }
            return temp != null ? temp.resistencia : 0.15;
        }
    }

    class ListaSimpleTratamientos {
        class Nodo {
            String detalle;
            Nodo siguiente;
            Nodo(String d) { this.detalle = d; }
        }
        private Nodo cabeza = null;

        public void insertar(String detalle) {
            Nodo nuevo = new Nodo(detalle);
            nuevo.siguiente = cabeza;
            cabeza = nuevo;
        }
    }
}