package de.codesourcery.terrain;

/*
 * JOCL - Java bindings for OpenCL
 *
 * Copyright 2009 Marco Hutter - http://www.jocl.org/
 */

import com.badlogic.gdx.utils.Disposable;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.function.Consumer;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueue;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clSetKernelArg;

public class OpenCLExecutor implements Disposable
{
    private static final boolean DEBUG = false;

    private boolean initDone;

    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel kernel;

    private int bufferSize;

    private cl_mem relNeighbourOffsetBuffer;
    private cl_mem heightBuffer;
    private cl_mem waterBuffer;
    private cl_mem dstBuffer;

    public static void main(String[] args) throws Exception {

        final OpenCLExecutor executor = new OpenCLExecutor();
        final Data data = new Data(3 );
        data.setWater( 1,1,50f );

        System.out.println("Initial water: ");
        dumpBuffer(data.water,data.size);

        executor.flow(data);

        System.out.println("Final result: ");
        dumpBuffer(data.water,data.size);
        executor.dispose();
    }

    private static void dumpBuffer(FloatBuffer buffer,int rowSize)
    {
        buffer.rewind();
        for ( int y = 0 ; y < rowSize ; y++)
        {
            for ( int x = 0 ; x < rowSize ; x++)
            {
                System.out.print( buffer.get()+"   " );
            }
            System.out.println();
        }
    }

    private void setup(Data data)
    {
        if ( ! initDone )
        {
            System.out.println("Setting up OpenCL...");
            // The platform, device type and device number
            // that will be used
            final int platformIndex = 0;
            final long deviceType = CL_DEVICE_TYPE_ALL;
            final int deviceIndex = 0;

            // Enable exceptions and subsequently omit error checks in this sample
            CL.setExceptionsEnabled( true );

            // Obtain the number of platforms
            final int numPlatformsArray[] = new int[1];
            clGetPlatformIDs( 0, null, numPlatformsArray );
            final int numPlatforms = numPlatformsArray[0];

            // Obtain a platform ID
            final cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
            clGetPlatformIDs( platforms.length, platforms, null );
            final cl_platform_id platform = platforms[platformIndex];

            // Initialize the context properties
            final cl_context_properties contextProperties = new cl_context_properties();
            contextProperties.addProperty( CL_CONTEXT_PLATFORM, platform );

            // Obtain the number of devices for the platform
            final int numDevicesArray[] = new int[1];
            clGetDeviceIDs( platform, deviceType, 0, null, numDevicesArray );
            final int numDevices = numDevicesArray[0];

            // Obtain a device ID
            final cl_device_id devices[] = new cl_device_id[numDevices];
            clGetDeviceIDs( platform, deviceType, numDevices, devices, null );
            final cl_device_id device = devices[deviceIndex];

            // Create a context for the selected device
            context = clCreateContext(
                    contextProperties, 1, new cl_device_id[]{device},
                    null, null, null );

            // Create a command-queue for the selected device
            commandQueue = clCreateCommandQueue( context, device, 0, null );

            // Create the program from the source code
            program = clCreateProgramWithSource( context,
                    1, new String[]{getKernelSource()}, null, null );

            // Build the program
            clBuildProgram( program, 0, null, null, null, null );

            // Create the kernel
            kernel = clCreateKernel( program, "flow", null );

            initDone = true;
        }

        // Allocate the memory objects for the input- and output data
        final int elements = data.size*data.size;
        final boolean allocNeeded = waterBuffer == null || bufferSize != data.size;
        if ( allocNeeded )
        {
            if ( waterBuffer != null ) {
                System.out.println("Reallocating...");
                disposeDynamicMemory();
            }

            final int[] relNeighbourOffsets = {-data.size -1,
                    -data.size,
                    -data.size +1,
                    -1,
                    1,
                    data.size -1,
                    data.size,
                    data.size +1};

            relNeighbourOffsetBuffer = clCreateBuffer( context, CL_MEM_READ_ONLY |
                            CL_MEM_COPY_HOST_PTR,
                    Sizeof.cl_int * 8, Pointer.to(relNeighbourOffsets), null );

            waterBuffer = clCreateBuffer( context, CL_MEM_READ_ONLY,
                    Sizeof.cl_float * elements, null, null );
            heightBuffer = clCreateBuffer( context, CL_MEM_READ_ONLY,
                    Sizeof.cl_float * elements, null, null );
            dstBuffer = clCreateBuffer( context, CL_MEM_WRITE_ONLY,
                    Sizeof.cl_float * elements, null, null );

            bufferSize = data.size;

            // Set the arguments for the kernel
            /*
             * __kernel void flow(__global const float *height,
             *                   __global const float *water,
             *                   __global float *dst,
             *                   __global const int *relNeighbourOffsets)
             */
            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(heightBuffer));
            clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(waterBuffer));
            clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(dstBuffer));
            clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(relNeighbourOffsetBuffer));
        }
//        else {
        // no allocation needed, just upload buffer contents
        if ( DEBUG )
        {
            System.out.println( "Uploading height data" );
            dumpBuffer( data.height, data.size );
            System.out.println( "Uploading water data" );
            dumpBuffer( data.water, data.size );
        }

        CL.clEnqueueWriteBuffer( commandQueue, heightBuffer,true,0,
                Sizeof.cl_float * elements,Pointer.to(data.height),0,null,null);

        CL.clEnqueueWriteBuffer( commandQueue, waterBuffer,true,0,
                Sizeof.cl_float * elements,Pointer.to(data.water),0,null,null);
//        }
    }

    private String getKernelSource() {

        final String path = "/opencl/kernel.c";
//        final String path = "/opencl/test_kernel.c";
        if ( DEBUG ) {
            System.out.println("Using kernel "+path);
        }
        try ( final InputStream in = getClass().getResourceAsStream( path ) )
        {
            if ( in == null ) {
                throw new RuntimeException("Failed to load OpenCL kernel from classpath "+path);
            }
            final String src = new String(in.readAllBytes(),"UTF8");
            if ( DEBUG ) {
                System.out.println("---------------------------");
                System.out.println(src);
                System.out.println("---------------------------");
            }
            return src;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void disposeDynamicMemory()
    {
        waterBuffer = safeRelease(waterBuffer, CL::clReleaseMemObject );
        heightBuffer = safeRelease(heightBuffer, CL::clReleaseMemObject );
        dstBuffer = safeRelease(dstBuffer, CL::clReleaseMemObject );
        relNeighbourOffsetBuffer = safeRelease(relNeighbourOffsetBuffer, CL::clReleaseMemObject );
    }

    public void dispose()
    {
        // Release kernel, program, and memory objects
        disposeDynamicMemory();

        kernel = safeRelease( kernel, CL::clReleaseKernel );
        program = safeRelease( program, CL::clReleaseProgram );
        commandQueue = safeRelease( commandQueue, CL::clReleaseCommandQueue );
        context = safeRelease( context, CL::clReleaseContext );

        initDone = false;
    }

    public void flow(Data data) {

        try
        {
            setup( data );
        }
        catch(RuntimeException e) {
            try {
                dispose();
            } catch(Exception e2) { }
            throw e;
        }

        // Set the work-item dimensions
        final long global_work_size[] = new long[]{data.size*data.size};
        final long local_work_size[] = new long[]{1};

        if ( DEBUG )
        {
            System.out.println( "Invoking kernel" );
        }

        // Execute the kernel
        int result = clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
                global_work_size, local_work_size, 0, null, null);

        if ( DEBUG )
        {
            System.out.println("Enqueue returned: "+result);
            data.tmp.rewind();
            for ( int i = 0 ; i < data.size*data.size ; i++) {
                data.tmp.put(0);
            }
            System.out.println("TMP buffer before invoking kernel");
            dumpBuffer( data.tmp, data.size );
            data.tmp.rewind();
        }

        // Read the result
        final int elements = data.size*data.size;
        result = clEnqueueReadBuffer(commandQueue, dstBuffer, CL_TRUE, 0,
                elements * Sizeof.cl_float, Pointer.to(data.tmp), 0, null, null);

        if ( DEBUG )
        {
            System.out.println("readBuffer returned: "+result);
            dumpBuffer( data.tmp, data.size );
        }

        data.tmp.rewind();
        for ( int i = 0, len = data.size*data.size ; i < len ; i++ )
        {
            final float delta = data.tmp.get();
            final float value = data.water.get(i) + delta;
            data.water.put(i,value);
        }
    }

    private static <T> T safeRelease(T value, Consumer<T> func) {
        if ( value != null )
        {
            func.accept( value );
        }
        return null;
    }
}