package de.codesourcery.terrain;

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

    private static final int WATER_MINHEIGHT = 210;

    private static final float START_SCALE = 1f;
    private static final float SCALE_REDUCE = 0.5f;
    private static final int RND_RANGE = 20;
    private static final int INITAL_SIZE = 257;
    private static final int FLOW_STEPS = 10;

    public enum Mode
    {
        WATER, HEIGHT;
    }

    private File mostRecentFile;
    private Mode mode = Mode.WATER;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    private final Point tmp = new Point();

    private static final int[] WATER_GRADIENT = new int[256];

    static
    {
        for (int i = 0; i < 256; i++)
        {
//            WATER_GRADIENT[i] = i<<24 | i;
            WATER_GRADIENT[i] = 0xff0000ff;
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
                            data.setWater( p.x, p.y, value );
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
                                    applyValue( p, 255 );
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
                                        applyValue( p, 0 );
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
                        if ( timer.isRunning() ) {
                            System.out.println("Stopping timer");
                            timer.stop();
                        } else {
                            System.out.println("Starting timer");
                            timer.start();
                        }
                    }
                }

                @Override
                public void keyTyped(KeyEvent e)
                {
                    switch ( e.getKeyChar() )
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
                        case 'c':
                            data.clear();
                            break;
                        case 'w':
                            data.initWater( WATER_MINHEIGHT );
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

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent( g );

            final BufferedImage image = image( data.size, data.size );
            for (int x = 0; x < data.size; x++)
            {
                for (int y = 0; y < data.size; y++)
                {
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
            final int size = data.size;
            for (int x = 0; x < size; x++)
            {
                for (int y = 0; y < size; y++)
                {
                    float w = data.water( x, y );
                    if ( w > 0 )
                    {
//                        image.setRGB(x,y,WATER_GRADIENT[w]);
                        image.setRGB( x, y, Color.BLUE.getRGB() );
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
                int y = 20;
                final int fontHeight = 20;
                g.drawString( "MODE: " + mode, 10, y );
                y += fontHeight;
                g.drawString( "Position: " + squareX + " / " + squareY, 10, y );
                y += fontHeight;
                g.drawString( "Height: " + data.height( squareX, squareY ), 10, y );
                y += fontHeight;
                g.drawString( "Water: " + data.water( squareX, squareY ), 10, y );
                y += fontHeight;
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

    private final Timer timer = new Timer(16, ev ->
    {
        for ( int i = 0 ; i < FLOW_STEPS ; i++)
        {
            data.flow();
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