package de.codesourcery.terrain;

import com.badlogic.gdx.math.Vector3;

public class IntPointStack
{
    private int[] vertices=new int[0];
    private int stackPtr;

    public void clear()
    {
        stackPtr = 0;
    }

    public void push(int x,int y)
    {
        assureSize();
        int ptr = stackPtr;
        vertices[ptr++] = x;
        vertices[ptr++] = y;
        this.stackPtr = ptr;
    }

    public boolean isEmpty() {
        return stackPtr == 0;
    }

    public void pop(Vector3 result) {
        if ( isEmpty() ) {
            throw new IllegalStateException( "Stack is empty" );
        }
        int ptr = stackPtr;
        result.y = vertices[--ptr];
        result.x = vertices[--ptr];
        this.stackPtr = ptr;
    }

    public int size() {
        return stackPtr /2;
    }

    private void assureSize()
    {
        if ( stackPtr == vertices.length )
        {
            final int maxPoints = vertices.length/2;
            final int[] tmp = new int[ maxPoints+1+maxPoints/2 ];
            System.arraycopy( this.vertices,0,tmp,0,this.vertices.length);
            this.vertices = tmp;
        }
    }
}
