package de.codesourcery.terrain;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class Main extends JFrame
{
    private static final boolean COLORIZE = false;
    private static final boolean NORMALIZE = true;

    private static final int WATER_MINHEIGHT = 240;

    private static final float START_SCALE = 1f;
    private static final float SCALE_REDUCE = 0.7f;
    private static final int RND_RANGE = 10;
    private static final int SIZE = 257;

    private static final int[] WATER_GRADIENT = new int[256];

    static {
        for ( int i = 0 ; i < 256 ; i++ ) {
//            WATER_GRADIENT[i] = i<<24 | i;
            WATER_GRADIENT[i] = 0xff0000ff;
        }
    }

    private final Data data = generateTerrain( 0xdeadbeef, new Data(SIZE ) );

    private static Data generateTerrain(long seed, Data data) {
        data.clearWater();
        data.initHeights(seed,START_SCALE,RND_RANGE,SCALE_REDUCE,NORMALIZE);
        return data;
    }

    private static final class ColorAndPosition {

        public final int color;
        public final float position;

        private ColorAndPosition(int color, float position)
        {
            if ( position < 0.0f || position >1.0f) {
                throw new IllegalArgumentException( "position must be 0...1" );
            }
            this.color = color;
            this.position = position;
        }

        public float r() { return ( (color >> 16 ) & 0xff ) / 255.0f; }
        public float g() { return ((color >> 8  ) & 0xff)/255.0f; }
        public float b() { return (color & 0xff)/255.0f; }
    }

    private static final class GradientBuilder {

        private final List<ColorAndPosition> colors = new ArrayList<>();

        public GradientBuilder addColor(java.awt.Color color,float position) {
            return addColor( color.getRGB() , position );
        }
        public GradientBuilder addColor(int color,float position) {
            colors.add( new ColorAndPosition( color,position ) );
            return this;
        }

        public int[] buildGradient(int steps)
        {
            final int[] result = new int[steps];
            colors.sort( (a,b) -> Float.compare( a.position, b.position ) );
            ColorAndPosition previous = colors.get( 0 );
            for (int i = 1, colorsSize = colors.size(); i < colorsSize; i++)
            {
                ColorAndPosition current  = colors.get( i );
                int startIdx = (int) (steps*previous.position);
                int endIdx = (int) (steps*current.position);
                float delta = endIdx - startIdx;
                float dr = (current.r()- previous.r() ) / delta;
                float dg = (current.g()- previous.g() ) / delta;
                float db = (current.b()- previous.b() ) / delta;

                float r = previous.r();
                float g = previous.g();
                float b = previous.b();
                for ( int j = startIdx ; j < endIdx ; j++ )
                {
                    result[j] = 0xff000000 |
                            ( (int) (r*255) << 16) | ( (int) (g*255) << 8 ) | (int) (b*255);
                    r = Math.max(0,Math.min(r+dr,255));
                    g = Math.max(0,Math.min(g+dg,255));
                    b = Math.max(0,Math.min(b+db,255));
                }
                previous = current;
            }
            return result;
        }

    }
    private final class MyPanel extends JPanel
    {
        private BufferedImage image;
        private Graphics2D imageGfx;

        private final int[] gradient = new GradientBuilder()
                .addColor( Color.BLUE , 0 )
                .addColor( Color.GREEN, 0.4f )
                .addColor( new Color(182,22,0), 0.5f )
                .addColor( Color.GRAY, 0.8f )
                .addColor( Color.WHITE, 1.0f )
                .buildGradient( 256 );

        public MyPanel() {

            setFocusable( true );
            requestFocus();
            addKeyListener( new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    if ( e.getKeyCode() == KeyEvent.VK_W ) {
                        System.out.println("Water initialized @ "+WATER_MINHEIGHT);
                        data.initWater( WATER_MINHEIGHT);
                    }
                    else if ( e.getKeyCode() == KeyEvent.VK_SPACE ) {
                        System.out.println("STEP");
                        data.flow();
                    }
                    else
                    {
                        System.out.println("Generating terrain...");
                        generateTerrain( System.currentTimeMillis(), data );
                    }
                    repaint();
                }
            } );
        }

        private BufferedImage image(int w,int h) {

            if ( image == null || image.getWidth() != w || image.getHeight() != h ) {
                if ( image != null )
                {
                    imageGfx.dispose();
                    image = null;
                }
                image = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                imageGfx = image.createGraphics();
            }
            return image;
        }

        private Graphics2D gfx(int w,int h) {
            image(w,h);
            return imageGfx;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent( g );

            final BufferedImage image = image(data.size,data.size);
            for ( int x = 0 ; x < data.size ; x++ )
            {
                for ( int y= 0 ; y < data.size ; y++ )
                {
                    final int v = data.height(x,y);
                    final int color;
                    if ( COLORIZE )
                    {
                        color = gradient[v];
                    }
                    else
                    {
                        color = v << 16 | v << 8 | v;
                    }
                    image.setRGB( x,y, 0xff000000 | (color & ~0xff000000));
                }
            }

            // draw water
            for ( int x = 0 ; x < SIZE ; x++)
            {
                for (int y = 0; y < SIZE; y++)
                {
                    int w = data.water(x,y);
                    if ( w > 0 ) {
                        image.setRGB(x,y,WATER_GRADIENT[w]);
                    }
                }
            }

            g.drawImage(image,0,0, getWidth(), getHeight(), null);

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
        }
    }

    public Main() {
        setTitle("Terrain");
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        final MyPanel panel = new MyPanel();
        panel.setPreferredSize( new Dimension(640,480) );
        getContentPane().add( panel );
        pack();
        setLocationRelativeTo( null );
        setVisible(true);
    }

    public static void main( String[] args ) throws InvocationTargetException, InterruptedException
    {
        SwingUtilities.invokeAndWait( () -> new Main() );
    }
}