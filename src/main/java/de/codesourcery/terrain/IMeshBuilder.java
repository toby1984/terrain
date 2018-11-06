package de.codesourcery.terrain;

import com.badlogic.gdx.math.Vector3;

public interface IMeshBuilder
{
    public void addTriangle(Vector3 p0,Vector3 p1,Vector3 p2);
}
