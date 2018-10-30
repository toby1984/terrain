package de.codesourcery.terrain;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;
import com.badlogic.gdx.math.Vector3;

import java.awt.*;
import java.util.Arrays;

public class Renderer
{
    private static final float TILE_SIZE = 5f;

    public final float viewportX=0;
    public final float viewportY=0;

    public final PerspectiveCamera camera = new PerspectiveCamera();

    public final FirstPersonCameraController cameraController = new FirstPersonCameraController( camera );

    private final TriangleList mesh1 = new TriangleList();

    private Data data;

    static {
        System.loadLibrary( "gdx64" );
    }

    private OpenGLRenderer glRenderer = new OpenGLRenderer();

    public Renderer()
    {
        cameraController.setVelocity( 50 );
        camera.position.set( new Vector3(0.0f,-12.5f,-25.05f) );
        camera.direction.set( new Vector3(0.0f,0.0f,-1.0f) );
        camera.update(true);
    }

    public void setData(Data d) {
        this.data = d;
        mesh1.setupMesh( d, TILE_SIZE );
    }

    public void render(Graphics2D gfx)
    {
        if ( data == null) {
            return;
        }
//        mesh1.setToCube( 5f );
    }
}
