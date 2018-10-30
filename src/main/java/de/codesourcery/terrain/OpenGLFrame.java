package de.codesourcery.terrain;

import com.badlogic.gdx.backends.lwjgl.LwjglAWTCanvas;
import com.badlogic.gdx.graphics.PerspectiveCamera;

import javax.swing.*;
import java.awt.*;

public class OpenGLFrame extends JFrame
{
    public final OpenGLRenderer renderer;
    private final LwjglAWTCanvas canvas;

    public OpenGLFrame() throws HeadlessException
    {
        super("OpenGL");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        renderer = new OpenGLRenderer();
        canvas = new LwjglAWTCanvas(renderer);
        getContentPane().add(canvas.getCanvas(), BorderLayout.CENTER);
    }

    public PerspectiveCamera camera() {
        return renderer.camera;
    }

    @Override
    public void dispose()
    {
        canvas.exit();
        super.dispose();
    }

    public void changeVisibility(boolean show, Window window)
    {
        if ( show )
        {
            setSize( window.getSize() );
            setLocation( window.getLocation() );
            setVisible(true);
            renderer.resume();
        }
        else
        {
            renderer.pause();
            setVisible(false);
        }
    }
}