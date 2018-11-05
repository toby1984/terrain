package de.codesourcery.terrain;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
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

    private ShaderProgram flowShaders;
    private FloatTexture heightMap;
    private FloatTexture waterMap;

    public OpenGLRenderer() {
        camera = new PerspectiveCamera();
        camera.fieldOfView = 67;
        camera.position.set(0f, 100f, 50f);
        camera.lookAt(0,0,0);
        camera.near = 0.1f;
        camera.far = 1000f;
    }

    private FloatTexture mapBeRealloc(FloatTexture tex,int desiredWidth,int desiredHeight)
    {
        if ( tex == null )
        {
            return FloatTexture.newInstance( desiredWidth, desiredHeight );
        }
        if ( ! tex.hasSize( desiredWidth,desiredHeight ) )
        {
            tex.dispose();
            return FloatTexture.newInstance( desiredWidth, desiredHeight );
        }
        return tex;
    }

    public void setData(Data data)
    {
        synchronized(MODEL_LOCK)
        {
            this.dataChanged = this.data == null || data.dirty;
            this.data = data;
            final int size = data.size;
            // note: Diamond-square algorithm requires that
            // heightmap is (Power-Of-Two + 1) but
            // OpenGL works best with Power-Of-Two textures...
//            this.heightMap = mapBeRealloc(this.heightMap,size-1,size-1);
//            this.waterMap = mapBeRealloc(this.heightMap,size-1,size-1);
        }
    }

    private static final class FloatTexture implements Disposable
    {
        public final FloatBuffer buffer;
        public final int textureHandle;
        public final int width;
        public final int heigth;
        public boolean needsUpload = true;

        private FloatTexture(int textureHandle, int width, int height)
        {
            this.buffer = Data.newFloatBuffer( width * height );
            this.textureHandle = textureHandle;
            this.width = width;
            this.heigth = height;
        }

        public boolean hasSize(int width,int height) {
            return this.width == width && this.heigth == heigth;
        }

        public void uploadBuffer(int textureUnit)
        {
            Gdx.gl20.glActiveTexture(textureUnit);
            Gdx.gl.glBindTexture( GL20.GL_TEXTURE_2D, textureHandle );
            if ( Gdx.gl.glGetError() != 0 )
            {
                throw new RuntimeException( "Something went wrong" );
            }
            if ( needsUpload )
            {
                Gdx.gl.glTexImage2D( GL20.GL_TEXTURE_2D,
                        0, // mip-map level
                        GL20.GL_RGB, // number of color components
                        width,
                        heigth,
                        0, // must always be set to 0
                        GL20.GL_RGB, // format of pixel data
                        GL20.GL_FLOAT, // data type
                        buffer );
                if ( Gdx.gl.glGetError() != 0 )
                {
                    throw new RuntimeException( "Something went wrong" );
                }
                needsUpload = false;
            }
        }

        public static FloatTexture newInstance(int width,int height)
        {
            // allocate texture handle
            final IntBuffer intBuffer = IntBuffer.allocate( 1 );
            Gdx.gl.glGenTextures( 1, intBuffer );
            final int texHandle = intBuffer.get(0);
            if ( Gdx.gl.glGetError() !=0 ) {
                throw new RuntimeException("Something went wrong: ");
            }
            return new FloatTexture( texHandle,width,height);
        }

        @Override
        public void dispose()
        {
            Gdx.gl.glDeleteTexture( textureHandle );
        }
    }

    @Override
    public void create()
    {
        // setup shader to calculate water propagation
//        final String vertexSrc = "";
//        final String fragmentSrc = "";
//        flowShaders = new ShaderProgram(vertexSrc,fragmentSrc);
//        if ( ! flowShaders.isCompiled() ) {
//            throw new RuntimeException("Failed to compile shader");
//        }

        // setup libgdx stuff
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


    private void setupRenderingTarget()  {
        /*
        // The framebuffer, which regroups 0, 1, or more textures, and 0 or 1 depth buffer.
GLuint FramebufferName = 0;
glGenFramebuffers(1, &FramebufferName);
glBindFramebuffer(GL_FRAMEBUFFER, FramebufferName);

// The texture we're going to render to
GLuint renderedTexture;
glGenTextures(1, &renderedTexture);

// "Bind" the newly created texture : all future texture functions will modify this texture
glBindTexture(GL_TEXTURE_2D, renderedTexture);

// Give an empty image to OpenGL ( the last "0" )
glTexImage2D(GL_TEXTURE_2D, 0,GL_RGB, 1024, 768, 0,GL_RGB, GL_UNSIGNED_BYTE, 0);

// Poor filtering. Needed !
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

// The depth buffer
GLuint depthrenderbuffer;
glGenRenderbuffers(1, &depthrenderbuffer);
glBindRenderbuffer(GL_RENDERBUFFER, depthrenderbuffer);
glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, 1024, 768);
glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthrenderbuffer);

// Set "renderedTexture" as our colour attachement #0
glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, renderedTexture, 0);

// Set the list of draw buffers.
GLenum DrawBuffers[1] = {GL_COLOR_ATTACHMENT0};
glDrawBuffers(1, DrawBuffers); // "1" is the size of DrawBuffers

// Always check that our framebuffer is ok
if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
  throw new RuntimeException("Failed to setup framebuffer target");
}
         */
    }
    @Override
    public void render()
    {
        camController.update();

        if ( data == null ) {
            return;
        }

//        heightMap.uploadBuffer(GL20.GL_TEXTURE0);
//        waterMap.uploadBuffer(GL20.GL_TEXTURE1);
//
//        flowShaders.begin();
//        flowShaders.setUniformi( "heightMap", 0); // texture unit #0
//        flowShaders.setUniformi( "waterMap", 1); // texture unit #1
//        flowShaders.end();

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

            flowShaders = safeDispose( flowShaders );
            heightMap = safeDispose( heightMap );
            waterMap = safeDispose( waterMap );
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

    private static <T extends Disposable> T safeDispose(T value) {
        if ( value != null ) {
            value.dispose();
        }
        return null;
    }
}