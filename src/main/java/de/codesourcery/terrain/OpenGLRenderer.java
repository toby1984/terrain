package de.codesourcery.terrain;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

/**
 * See: http://blog.xoppa.com/basic-3d-using-libgdx-2/
 * @author Xoppa
 */
public class OpenGLRenderer implements ApplicationListener
{
    public Environment environment;
    public final PerspectiveCamera camera;
    public CameraInputController camController;
    public ModelBatch modelBatch;

    private final Object MODEL_LOCK = new Object();

    // @GuardedBy( MODEL_LOCK )
    public Model meshModel;

    // @GuardedBy( MODEL_LOCK )
    public ModelInstance modelInstance;

    // @GuardedBy( MODEL_LOCK )
    private final TriangleList list = new TriangleList();

    // @GuardedBy( MODEL_LOCK )
    private Data data;

    // @GuardedBy( MODEL_LOCK )
    private boolean dataChanged;

    public OpenGLRenderer() {
        camera = new PerspectiveCamera();
        camera.fieldOfView = 67;
        camera.position.set(10f, 10f, 10f);
        camera.lookAt(0,0,0);
        camera.near = 1f;
        camera.far = 300f;
    }

    public void setData(Data data)
    {
        System.out.println("GL renderer got data");
        synchronized(MODEL_LOCK)
        {
            this.dataChanged = this.data == null || data.dirty;
            this.data = data;
        }
    }

    @Override
    public void create()
    {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));

        final Vector3 lightDir = new Vector3(0,0,10);
//        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, lightDir));
        environment.add(new PointLight().set(0.8f, 0.8f, 0.8f, lightDir,100.0f));

        camera.viewportWidth = Gdx.graphics.getWidth();
        camera.viewportHeight = Gdx.graphics.getHeight();
        camera.update();

        camController = new CameraInputController( camera );
        Gdx.input.setInputProcessor(camController);

        modelBatch = new ModelBatch();

        ModelBuilder modelBuilder = new ModelBuilder();
        final Material material = new Material( ColorAttribute.createDiffuse( Color.GREEN ) );
        meshModel = modelBuilder.createBox(5f, 5f, 5f,
                material,
                Usage.Position | Usage.Normal);
        modelInstance = new ModelInstance( meshModel );
    }

    @Override
    public void render()
    {
        camController.update();

        if ( data == null ) {
            return;
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin( camera );
        synchronized(MODEL_LOCK)
        {
            if ( modelInstance == null || dataChanged || data.dirty )
            {
                setupModel();
                dataChanged = false;
            }
            modelBatch.render( modelInstance, environment );
        }
        modelBatch.end();
    }

    private void setupModel()
    {
        System.out.println("GL renderer rebuilds mesh");

        list.clear();
//        list.setToCube( 5f );
        list.setupMesh( data, 10f );

        if ( meshModel != null )
        {
            meshModel.dispose();
            meshModel = null;
        }

        final ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        final Material material = new Material( ColorAttribute.createDiffuse( Color.GREEN ) );
        final MeshPartBuilder part1 = modelBuilder.part( "part1", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, material );
        part1.addMesh( list.vertices, list.indices );

        this.meshModel = modelBuilder.end();
        this.modelInstance = new ModelInstance( this.meshModel );
    }

    @Override
    public void dispose()
    {
        synchronized( MODEL_LOCK )
        {
            modelBatch.dispose();
            meshModel.dispose();
            meshModel = null;
            modelBatch = null;
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }
}