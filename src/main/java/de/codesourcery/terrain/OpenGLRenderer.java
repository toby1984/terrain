package de.codesourcery.terrain;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * See: http://blog.xoppa.com/basic-3d-using-libgdx-2/
 * @author Xoppa
 */
public class OpenGLRenderer implements ApplicationListener
{
    public static final boolean RENDER_WATER =false;
    public Environment environment;
    public final PerspectiveCamera camera;
    public FirstPersonCameraController camController;
    public ModelBatch modelBatch;

    private final Object MODEL_LOCK = new Object();

    private static final class ModelAndInstance
    {
        public final Model model;
        public final ModelInstance modelInstance;

        private ModelAndInstance(Model model, ModelInstance instance)
        {
            this.model = model;
            this.modelInstance = instance;
        }

        public void dispose() {
            this.model.dispose();
        }
    }

    // @GuardedBy( MODEL_LOCK )
    private final List<ModelAndInstance> items =
            new ArrayList<>();

    // @GuardedBy( MODEL_LOCK )
    private final TriangleList heightMapMesh = new TriangleList();

    private final TriangleList waterMesh = new TriangleList();

    // @GuardedBy( MODEL_LOCK )
    private Data data;

    // @GuardedBy( MODEL_LOCK )
    private boolean dataChanged;

    public OpenGLRenderer() {
        camera = new PerspectiveCamera();
        camera.fieldOfView = 67;
        camera.position.set(0f, 100f, 50f);
        camera.lookAt(0,0,0);
        camera.near = 0.1f;
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
        environment.set( IntAttribute.createCullFace( GL20.GL_FRONT ));

        final Vector3 lightDir1 = new Vector3(0,0,1);
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, lightDir1));

        camera.viewportWidth = Gdx.graphics.getWidth();
        camera.viewportHeight = Gdx.graphics.getHeight();
        camera.update();

        camController = new FirstPersonCameraController( camera );
        camController.setVelocity( 40 );
        Gdx.input.setInputProcessor(camController);

        modelBatch = new ModelBatch();

        final ModelBuilder modelBuilder = new ModelBuilder();
        final Material material = new Material( ColorAttribute.createDiffuse( Color.GREEN ) );
        final Model meshModel = modelBuilder.createBox(5f, 5f, 5f,
                material, Usage.Position | Usage.Normal);
        final ModelInstance modelInstance = new ModelInstance( meshModel );
        this.items.add( new ModelAndInstance( meshModel, modelInstance ) );
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
            if ( items.isEmpty() || dataChanged || data.dirty )
            {
                setupModel();
                dataChanged = false;
            }
            for (int i = 0, itemsSize = items.size(); i < itemsSize; i++)
            {
                final ModelAndInstance x = items.get( i );
                modelBatch.render( x.modelInstance , environment );
            }
        }
        modelBatch.end();
    }

    private void setupModel()
    {
        System.out.println("GL renderer rebuilds mesh");

        if ( ! items.isEmpty() )
        {
            items.forEach(  x -> x.dispose()  );
            items.clear();
        }

        final ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        final Material material = new Material();
        VertexAttribute attr1 = VertexAttribute.Position();
        VertexAttribute attr2 = VertexAttribute.Normal();
        VertexAttribute attr3 = VertexAttribute.ColorUnpacked();
        VertexAttributes attrs = new VertexAttributes( attr1,attr2,attr3 );
        final MeshPartBuilder part1 = modelBuilder.part( "part1", GL20.GL_TRIANGLES, attrs, material );

        heightMapMesh.clear();
//        list.setToCube( 5f );
        final float tileSize = 5f;
        heightMapMesh.setupHeightMesh( data, tileSize, Main.TERRAIN_GRADIENT );
        heightMapMesh.compact();
        part1.addMesh( heightMapMesh.vertices, heightMapMesh.indices );

        if ( RENDER_WATER )
        {
            waterMesh.clear();
            waterMesh.setupWaterMesh( data, tileSize );
            waterMesh.compact();
            part1.addMesh( waterMesh.vertices, waterMesh.indices );
        }

        final Model model = modelBuilder.end();
        final ModelInstance instance = new ModelInstance( model );
        items.add( new ModelAndInstance( model,instance ) );
    }

    @Override
    public void dispose()
    {
        synchronized( MODEL_LOCK )
        {
            modelBatch.dispose();
            items.forEach( x -> x.dispose() );
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