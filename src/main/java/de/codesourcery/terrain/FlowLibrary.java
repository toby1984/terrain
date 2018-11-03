package de.codesourcery.terrain;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.nio.FloatBuffer;

public interface FlowLibrary extends Library
{
    FlowLibrary INSTANCE = (FlowLibrary)
            Native.load(("flow"),FlowLibrary.class);

    void flowRepeat(int size, FloatBuffer height,FloatBuffer water,int repeat);

    void flow(int size, FloatBuffer height, FloatBuffer water);
}
