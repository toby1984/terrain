package de.codesourcery.terrain;

public class PointList
{
    private int x[]=new int[0];
    private int y[]=new int[0];

    private int ptr = 0;

    public interface IVisitor {
        public void visit(int x,int y,float data);
    }

    public void add(int px,int py) {
        if ( ptr == x.length )
        {
            final int newLength = x.length + (x.length/2) +1;
            int[] tmp = new int[newLength];
            System.arraycopy( x,0,tmp,0,x.length );
            x = tmp;

            tmp = new int[newLength];
            System.arraycopy( y,0,tmp,0,y.length );
            y = tmp;
        }
        x[ptr] = px;
        y[ptr] = py;
        ptr++;
    }

    public void clear() {
        ptr = 0;
    }

    public int size() {
        return ptr;
    }

    public void forEach(float data,IVisitor v) {

        final int len = ptr;
        for ( int i = 0 ; i < len ; i++ ) {
            v.visit(x[i],y[i],data);
        }
    }
}