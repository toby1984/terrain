package de.codesourcery.terrain;

import com.badlogic.gdx.math.Vector3;

/**
 * Array-based stack implementation that is used to
 * keep track of x/y coordinates to visit during flood-filling
 * to avoid running into StackOverflowExceptions.
 */
public class IntPointStack
{
    private int[] vertices=new int[0];
    private int stackPtr;

    public void clear()
    {
        stackPtr = 0;
    }

    public void compact()
    {
        if ( vertices.length > stackPtr ) {
            realloc( size() );
        }
    }

    public int peekX() {
        if ( isEmpty() ) {
            throw new IllegalStateException( "Empty" );
        }
        return vertices[stackPtr-2];
    }

    public int peekY() {
        if ( isEmpty() ) {
            throw new IllegalStateException( "Empty" );
        }
        return vertices[stackPtr-1];
    }

    public void pop()
    {
        if ( isEmpty() ) {
            throw new IllegalStateException( "Empty" );
        }
        stackPtr -= 2;
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
            realloc(maxPoints+1+maxPoints/2 );
        }
    }

    private void realloc(int newPointCount)
    {
        final int[] tmp = new int[ newPointCount*2 ];
        final int toCopy = Math.min( this.vertices.length, newPointCount*2 );
        System.arraycopy( this.vertices,0,tmp,0,toCopy);
        this.vertices = tmp;
    }
}