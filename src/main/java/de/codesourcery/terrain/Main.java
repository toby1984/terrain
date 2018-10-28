package de.codesourcery.terrain;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

public class Main extends JFrame
{
    private static final File CONFIG_FILE = new File(".terraincfg");

    private static final boolean COLORIZE = false;
    private static final boolean NORMALIZE = true;

    private static final float FPS = 4;

    private static final int WATER_MINHEIGHT = 1;
    private static final int WATER_AMOUNT = 10;

    private static final float START_SCALE = 1f;

    private static final float SCALE_REDUCE = 0.5f;

    private static final int RND_RANGE = 100;

    private static final int INITAL_SIZE = 3;

    private static final int FLOW_STEPS = 10;

    public enum Mode {WATER,HEIGHT}

    private File mostRecentFile;
    private Mode mode = Mode.WATER;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    private final Point tmp = new Point();

    private static final int[] WATER_GRADIENT = new int[256];

    static
    {
        int bucketCount = 4;
        final int bucketLen = 256/bucketCount;
        float step = 1f/bucketCount;
        float b = 0;
        int idx = 0;
        for (int i = 0; i < bucketCount ; i++,b+=step)
        {
            for ( int j =0 ; j < bucketLen ; j++)
            {
                final int bi = (int) (b * 255f);
                WATER_GRADIENT[idx++] = 0x80000000 | (255 - bi);
            }
        }
    }

    private Data data = generateTerrain( 0xdeadbeef, new Data( INITAL_SIZE ) );

    private static Data generateTerrain(long seed, Data data)
    {
        data.clearWater();
        data.initHeights( seed, START_SCALE, RND_RANGE, SCALE_REDUCE, NORMALIZE );
        return data;
    }

    private final class MyPanel extends JPanel
    {
        private BufferedImage image;
        private Graphics2D imageGfx;
        private Renderer renderer = new Renderer();

        private boolean render2D=true;

        private Point highlight = null;

        private final int[] gradient = new GradientBuilder()
                .addColor( Color.BLUE, 0 )
                .addColor( Color.GREEN, 0.4f )
                .addColor( new Color( 182, 22, 0 ), 0.5f )
                .addColor( Color.GRAY, 0.8f )
                .addColor( Color.WHITE, 1.0f )
                .buildGradient( 256 );

        public MyPanel()
        {
            setFocusable( true );
            requestFocus();
            final MouseAdapter mouseAdapter = new MouseAdapter()
            {
                private boolean isDragging;
                private final Point dragStartPos = new Point();
                private float dragStartValue;

                private int dragStartX,dragStartY;

                private final Vector3 tmp = new Vector3();

                private void handleDrag(MouseEvent e)
                {
                    final float degreesPerPixel = 0.05f;
                    int dx = e.getX() - dragStartX;
                    int dy = e.getY() - dragStartY;
                    float deltaX = -dx * degreesPerPixel;
                    float deltaY = -dy * degreesPerPixel;
                    renderer.camera.direction.rotate(renderer.camera.up, deltaX);
                    tmp.set(renderer.camera.direction).crs(renderer.camera.up).nor();
                    renderer.camera.direction.rotate(tmp, deltaY);
                    renderer.camera.update();
                }

                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if ( ! render2D )
                    {
                        if ( isDragging ) {
                            handleDrag(e);
                            repaint();
                        } else {
                            isDragging = true;
                            dragStartX = e.getX();
                            dragStartY = e.getY();
                        }
                        return;
                    }

                    final Point p = point(e);
                    if ( p != null )
                    {
                        if ( !isDragging )
                        {
                            dragStartPos.setLocation( p );
                            isDragging = true;
                            switch(mode) {
                                case WATER:
                                    dragStartValue = data.water(p.x,p.y);
                                    break;
                                case HEIGHT:
                                    dragStartValue = data.height(p.x,p.y);
                                    break;
                                default:
                                    throw new RuntimeException("Unhandled switch/case: "+mode);
                            }
                        }
                        else if ( ! p.equals( dragStartPos ) )
                        {
                            switch(mode) {
                                case WATER:
                                    data.setWater(p.x,p.y,dragStartValue);
                                    break;
                                case HEIGHT:
                                    data.setHeight( p.x,p.y,(int) dragStartValue );
                                    break;
                                default:
                                    throw new RuntimeException("Unhandled switch/case: "+mode);
                            }
                            repaint();
                        }
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e)
                {
                    final Point p = point( e );
                    if ( p != null )
                    {
                        if ( highlight == null || ! highlight.equals(p) )
                        {
                            highlight = new Point( p );
                            repaint();
                        }
                    }
                    else
                    {
                        if ( highlight != null )
                        {
                            highlight = null;
                            repaint();
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    isDragging = false;
                }

                private void applyValue(Point p, int value)
                {
                    switch ( mode )
                    {
                        case WATER:
                            data.incWater(p.x,p.y,value);
                            break;
                        case HEIGHT:
                            data.incHeight( p.x, p.y, value );
                            break;
                        default:
                            throw new RuntimeException( "Unhandled switch/case: " + mode );
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e)
                {
                    final Point p = point( e );
                    if ( p != null )
                    {
                        final boolean leftButton = e.getButton() == MouseEvent.BUTTON1;
                        if ( leftButton )
                        {
                            switch ( mode )
                            {
                                case WATER:
                                    applyValue( p, 10 );
                                    break;
                                case HEIGHT:
                                    applyValue( p, 10 );
                                    break;
                                default:
                                    throw new RuntimeException( "Unhandled switch/case: " + mode );
                            }
                            repaint();
                        }
                        else
                        {
                            final boolean rightButton = e.getButton() == MouseEvent.BUTTON3;
                            if ( rightButton )
                            {
                                switch ( mode )
                                {
                                    case WATER:
                                        applyValue( p, -10 );
                                        break;
                                    case HEIGHT:
                                        applyValue( p, -10 );
                                        break;
                                    default:
                                        throw new RuntimeException( "Unhandled switch/case: " + mode );
                                }
                                repaint();
                            }
                        }
                    }
                }
            };
            addMouseListener( mouseAdapter );
            addMouseMotionListener( mouseAdapter );

            addKeyListener( new KeyAdapter()
            {
                private void keyPressOrRelease(KeyEvent e,boolean keyUp)
                {
                    final int key;
                    switch ( e.getKeyChar() )
                    {
                        case 'w':
                            key = Input.Keys.W;
                            break;
                        case 'a':
                            key = Input.Keys.A;
                            break;
                        case 's':
                            key = Input.Keys.S;
                            break;
                        case 'd':
                            key = Input.Keys.D;
                            break;
                        case 'q':
                            key = Input.Keys.Q;
                            break;
                        case 'e':
                            key = Input.Keys.E;
                            break;
                        default:
                            return;
                    }
                    if ( keyUp )
                    {
                        renderer.cameraController.keyUp( key );
                    } else {
                        renderer.cameraController.keyDown( key );
                    }
                    System.out.println("CAMERA POSITION: new Vector3("
                            +renderer.camera.position.x+"f,"
                            +renderer.camera.position.y+"f,"
                            +renderer.camera.position.z+"f)");
                    System.out.println("CAMERA DIRECTION: new Vector3("
                            +renderer.camera.direction.x+"f,"
                            +renderer.camera.direction.y+"f,"
                            +renderer.camera.direction.z+"f)");
                    repaint();
                }

                @Override
                public void keyPressed(KeyEvent e)
                {
                    if ( ! render2D )
                    {
                        keyPressOrRelease( e, false );
                    }
                }

                @Override
                public void keyReleased(KeyEvent e)
                {

                    if ( e.getKeyCode() == KeyEvent.VK_T) {
                        if ( waterSimulationRunning ) {
                            System.out.println("Stopping timer");
                            waterSimulationRunning = false;
                        } else {
                            System.out.println("Starting timer");
                            waterSimulationRunning = true;
                        }
                    }

                    if ( ! render2D )
                    {
                        keyPressOrRelease( e, true );
                    }
                }

                @Override
                public void keyTyped(KeyEvent e)
                {
                    final char keyChar = e.getKeyChar();
                    switch( keyChar ) {
                        case 'v':
                            render2D = !render2D;
                            repaint();
                            return;
                        case 'c':
                            data.clear();
                            repaint();
                            return;
                    }

                    if ( ! render2D )
                    {
                        return;
                    }

                    switch ( keyChar )
                    {
                        case '+':
                            renderer.zoomIn();
                            break;
                        case '-':
                            renderer.zoomOut();
                            break;
                        case 't': // handled by keyReleased() already
                            return;
                        case 's':
                            JFileChooser choser = new JFileChooser(mostRecentFile);
                            if ( mostRecentFile != null ) {
                                choser.setSelectedFile( mostRecentFile );
                            }
                            if ( choser.showSaveDialog( null ) == JFileChooser.APPROVE_OPTION )
                            {
                                final File selectedFile = choser.getSelectedFile();
                                try (FileOutputStream out = new FileOutputStream( selectedFile ) ) {
                                    System.out.println("Saving data to "+ selectedFile );
                                    data.save( out );
                                    mostRecentFile = selectedFile;
                                    saveConfig();
                                }
                                catch (IOException e1)
                                {
                                    e1.printStackTrace();
                                }
                            }
                            break;
                        case 'l':
                            choser = new JFileChooser(mostRecentFile);
                            if ( mostRecentFile != null ) {
                                choser.setSelectedFile( mostRecentFile );
                            }
                            if ( choser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION )
                            {
                                final File selectedFile = choser.getSelectedFile();
                                try (FileInputStream in = new FileInputStream( selectedFile ) ) {
                                    System.out.println("Reading data from "+ selectedFile );
                                    data = Data.read( in );
                                    mostRecentFile = selectedFile;
                                    saveConfig();
                                }
                                catch (IOException e1)
                                {
                                    e1.printStackTrace();
                                }
                            }
                            break;
                        case 'm':
                            int idx = (mode.ordinal() + 1) % Mode.values().length;
                            mode = Mode.values()[idx];
                            break;
                        case 'w':
                            data.initWater( WATER_MINHEIGHT, WATER_AMOUNT);
                            break;
                        case ' ':
                            data.flow();
                            break;
                        default:
                            System.out.println( "Generating terrain..." );
                            generateTerrain( System.currentTimeMillis(), data );
                            break;
                    }
                    repaint();
                }
            } );
        }

        private BufferedImage image(int w, int h)
        {
            if ( image == null || image.getWidth() != w || image.getHeight() != h )
            {
                if ( image != null )
                {
                    imageGfx.dispose();
                    image = null;
                }
                image = new BufferedImage( w, h, BufferedImage.TYPE_INT_ARGB );
                imageGfx = image.createGraphics();
            }
            return image;
        }

        private Graphics2D gfx(int w, int h)
        {
            image( w, h );
            return imageGfx;
        }

        private long frames;

        @Override
        protected void paintComponent(Graphics g)
        {
            if ( ! timer.isRunning() ) {
                timer.start();
            }
            final long t1 = System.currentTimeMillis();
            if ( render2D ) {
                render2D( g );
            }
            else
            {
                render3D( g );
            }
            final long t2 = System.currentTimeMillis();
            if ( frames++ % 60 == 0 ) {
                System.out.println("paint() took "+(t2-t1)+" ms");
            }
        }

        private void render3D(Graphics g)
        {
            super.paintComponent(g);

            renderer.setData( data );
            renderer.camera.viewportHeight = getHeight();
            renderer.camera.viewportWidth = getWidth();
            renderer.camera.near = 0.1f;
            renderer.camera.far = 200f;
            renderer.camera.update();

            renderer.render((Graphics2D) g);
            renderUI(g);
        }

        private void render2D(Graphics g)
        {
            float minWater = Float.MAX_VALUE;
            float maxWater = Float.MIN_VALUE;
            final BufferedImage image = image( data.size, data.size );
            for (int x = 0; x < data.size; x++)
            {
                for (int y = 0; y < data.size; y++)
                {
                    final float w = data.water(x,y);
                    if ( w > 0f)
                    {
                        minWater = Math.min( minWater, w );
                        maxWater = Math.max( maxWater, w );
                    }
                    final int v = data.height( x, y );
                    final int color;
                    if ( COLORIZE )
                    {
                        color = gradient[v];
                    }
                    else
                    {
                        color = v << 16 | v << 8 | v;
                    }
                    image.setRGB( x, y, 0xff000000 | (color & ~0xff000000) );
                }
            }

            // draw water
            float offset;
            float scale;
            if ( minWater != Float.MIN_VALUE)
            {
                offset = minWater;
                scale = (WATER_GRADIENT.length-1) / (maxWater - minWater);
            } else {
                offset = 0;
                scale = 1;
            }
            final int size = data.size;
            for (int x = 0; x < size; x++)
            {
                for (int y = 0; y < size; y++)
                {
                    float w = data.water( x, y );
                    if ( w > 0 )
                    {
                        final int idx = (int) ((w-offset)*scale);
                        image.setRGB(x,y,WATER_GRADIENT[idx]);
                    }
                }
            }

            scaleX = getWidth() / (float) size;
            scaleY = getHeight() / (float) size;

            g.drawImage( image, 0, 0, getWidth(), getHeight(), null );

            // draw gradient
            if ( COLORIZE )
            {
                for (int i = 0; i < 256; i++)
                {
                    int x1 = i * 2;
                    int y1 = 10;
                    g.setColor( new Color( gradient[i] ) );
                    g.fillRect( x1, y1, 2, 10 );
                }
            }

            // draw highlight
            renderUI(g);
        }

        private void renderUI(Graphics g)
        {
            int y = 20;
            final int fontHeight = 20;
            if ( ! render2D ) {
                g.drawString( "Camera: "+renderer.camera.position,10,y);
                y += fontHeight;
                g.drawString( "Look-At: "+renderer.camera.direction,10,y);
                return;
            }
            if ( highlight != null )
            {
                g.setColor( Color.RED );
                final int squareX = highlight.x;
                final int squareY = highlight.y;
                final int x1 = (int) (squareX * scaleX);
                final int y1 = (int) (squareY * scaleY);
                final int x2 = (int) ((squareX + 1) * scaleX);
                final int y2 = (int) ((squareY + 1) * scaleY);
                g.drawRect( x1, y1, x2 - x1, y2 - y1 );
                g.setFont( getFont().deriveFont( 20f ) );
                g.drawString( "MODE: " + mode, 10, y );
                y += fontHeight;
                g.drawString( "Position: " + squareX + " / " + squareY, 10, y );
                y += fontHeight;
                g.drawString( "Height: " + data.height( squareX, squareY ), 10, y );
                y += fontHeight;
                g.drawString( "Water: " + data.water( squareX, squareY ), 10, y );
                y += fontHeight;
                g.drawString( "H+W: " + (data.height( squareX, squareY ) + data.water( squareX, squareY )), 10, y );
            }
        }
    }

    private void saveConfig()
    {
        final Properties props = new Properties();
        if ( mostRecentFile != null ) {
            props.setProperty( "mostRecentFile" , mostRecentFile.getAbsolutePath() );
        }
        try ( FileOutputStream out = new FileOutputStream(CONFIG_FILE))
        {
            props.store( out, "AUTO-GENERATED, DO NOT EDIT");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private final MyPanel panel = new MyPanel();

    private long tickCnt = 0;

    private boolean waterSimulationRunning = false;

    private long lastTickTime;

    private final Timer timer = new Timer((int) (1000f/FPS), ev ->
    {
        long time = System.currentTimeMillis();
        if ( lastTickTime != 0 )
        {
            float elapsedSeconds = (lastTickTime-time)/1000f;
            panel.renderer.cameraController.update(elapsedSeconds);
        }
        lastTickTime = time;

        if ( !waterSimulationRunning ) {
            panel.repaint();
            return;
        }

        long t1 = System.currentTimeMillis();
        for ( int i = 0 ; i < FLOW_STEPS ; i++)
        {
            data.flow();
        }
        long t2 = System.currentTimeMillis();
        tickCnt++;
        if ( tickCnt % 60 == 0 ) {
            System.out.println("flow() time: "+(t2-t1)+" ms");
        }
        panel.repaint();
    });

    public Main()
    {
        try
        {
            if ( CONFIG_FILE.exists() && CONFIG_FILE.canRead() )
            {
                final Properties props = new Properties();
                try ( FileInputStream in = new FileInputStream(CONFIG_FILE))
                {
                    props.load( in );
                    String value = props.getProperty( "mostRecentFile" );
                    if ( value != null ) {
                        mostRecentFile = new File(value);
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        setTitle( "Terrain" );
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        panel.setPreferredSize( new Dimension( 640, 480 ) );
        getContentPane().add( panel );
        pack();
        setLocationRelativeTo( null );
        setVisible( true );
    }

    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        SwingUtilities.invokeAndWait( () -> new Main() );
    }

    private Point point(MouseEvent e)
    {
        int squareX = (int) (e.getX() / scaleX);
        int squareY = (int) (e.getY() / scaleY);
        if ( squareX >= 0 && squareY >= 0 && squareX < data.size && squareY < data.size)
        {
            tmp.x = squareX;
            tmp.y = squareY;
            return tmp;
        }
        return null;
    }
}