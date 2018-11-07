package de.codesourcery.terrain;

import javax.swing.*;
import java.awt.*;
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

    private static final float FPS = 60;

    public static final boolean RENDER_OPENGL = true;
    private static final int WATER_MINHEIGHT = 1;
    private static final int WATER_AMOUNT = 10;

    private static final int RND_RANGE = 200;

    private static final int INITAL_SIZE = 129; // MUST be Power-Of-Two + 1 !!!!

    public enum Mode {WATER,HEIGHT}

    private File mostRecentFile;
    private Mode mode = Mode.WATER;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    private final Point tmp = new Point();

    public static final int[] WATER_GRADIENT = new int[256];

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
        data.initHeights( seed, RND_RANGE );
        return data;
    }

    public static final int[] TERRAIN_GRADIENT =
            new GradientBuilder()
            .addColor( Color.BLUE, 0 )
            .addColor( Color.GREEN, 0.4f )
            .addColor( new Color( 182, 22, 0 ), 0.5f )
            .addColor( Color.GRAY, 0.8f )
            .addColor( Color.WHITE, 1.0f )
            .buildGradient( 256 );

    private final class MyPanel extends JPanel
    {
        private BufferedImage image;
        private Graphics2D imageGfx;
        private OpenGLFrame glFrame = new OpenGLFrame();
        private boolean hudVisible = true;
        private boolean hideBorder = false;

        private boolean render2D=true;

        private Point highlight = null;

        public MyPanel()
        {
            setFocusable( true );
            requestFocus();
            final MouseAdapter mouseAdapter = new MouseAdapter()
            {
                private boolean isDragging;
                private final Point dragStartPos = new Point();
                private float dragStartValue;

                @Override
                public void mouseDragged(MouseEvent e)
                {
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
                        }
                        handleMove( e,true );
                    }
                }

                private void handleMove(MouseEvent e,boolean repaint)
                {
                    final Point p = point( e );
                    if ( p != null )
                    {
                        if ( highlight == null || ! highlight.equals(p) )
                        {
                            highlight = new Point( p );
                            if ( repaint )
                            {
                                repaint();
                            }
                        }
                    }
                    else
                    {
                        if ( highlight != null )
                        {
                            highlight = null;
                            if ( repaint )
                            {
                                repaint();
                            }
                        }
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e)
                {
                    handleMove(e,true);
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
                }

                @Override
                public void keyTyped(KeyEvent e)
                {
                    final char keyChar = e.getKeyChar();
                    switch( keyChar )
                    {
                        case 'v':
                            render2D = !render2D;
                            glFrame.changeVisibility( ! render2D, Main.this );
                            repaint();
                            return;
                        case 'x':
                            data.smooth();
                            repaint();
                            return;
                        case 'c':
                            data.clear();
                            repaint();
                            return;
                        case 'b':
                            hideBorder = ! hideBorder;
                            repaint();
                            return;
                    }

                    switch ( keyChar )
                    {
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
//                            data.setupWaterDebug( WATER_AMOUNT );
                            data.initWater( WATER_MINHEIGHT, WATER_AMOUNT);
                            break;
                        case ' ':
                            data.flow(1);
                            break;
                        case 'h':
                            hudVisible = ! hudVisible;
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
            if ( RENDER_OPENGL && data.dirty )
            {
                glFrame.renderer.setData( data );
                data.dirty = false;
            }
            render2D( g );
            final long t2 = System.currentTimeMillis();
            if ( frames++ % 60 == 0 ) {
                System.out.println("paint() took "+(t2-t1)+" ms");
            }
        }

        private void render2D(Graphics g)
        {
            float minWater = 10000000f;
            float maxWater = -10000000f;
            final BufferedImage image = image( data.size, data.size );

            imageGfx.setColor( Color.BLACK );
            imageGfx.fillRect( 0,0,getWidth(),getHeight());

            final int x0 = hideBorder ? 1 : 0;
            final int y0 = hideBorder ? 1 : 0;
            final int x1 = hideBorder ? data.size-1 : data.size;
            final int y1 = hideBorder ? data.size-1 : data.size;
            for (int y = y0; y < y1; y++)
            {
                for (int x = x0; x < x1; x++)
                {
                    final float w = data.water(x,y);
                    if ( w > 0f)
                    {
                        minWater = Math.min( minWater, w );
                        maxWater = Math.max( maxWater, w );
                    }
                    int v = (int) data.height( x, y );
                    v = (v<0) ? 0 : v;
                    final int color;
                    if ( COLORIZE )
                    {
                        color = TERRAIN_GRADIENT[v % TERRAIN_GRADIENT.length];
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
            for (int y = y0; y < y1; y++)
            {
                for (int x = x0; x < x1; x++)
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
                    int xa = i * 2;
                    int ya = 10;
                    g.setColor( new Color( TERRAIN_GRADIENT[i] ) );
                    g.fillRect( xa, ya, 2, 10 );
                }
            }

            // draw highlight
            renderUI(g);
        }

        private void renderUI(Graphics g)
        {
            if ( ! hudVisible ) {
                return;
            }
            int y = 20;
            final int fontHeight = 20;
            if ( ! render2D ) {
                // TODO: Accessing camera probably needs synchronized{} ...
                g.drawString( "Camera: "+ glFrame.camera().position,10,y);
                y += fontHeight;
                g.drawString( "Look-At: "+ glFrame.camera().direction,10,y);
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
    private long sumFlowTime = 0;

    private boolean waterSimulationRunning = false;

    private final Timer timer = new Timer((int) (1000f/FPS), ev ->
    {
        if ( !waterSimulationRunning ) {
            panel.repaint();
            return;
        }

        long t1 = System.currentTimeMillis();
        data.flow(10 );
        long t2 = System.currentTimeMillis();
        tickCnt++;
        sumFlowTime += (t2-t1);
        if ( tickCnt % 100 == 0 ) {
            System.out.println(tickCnt+" - flow() time: "+(t2-t1)+" ms (total: "+sumFlowTime+" ms");
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