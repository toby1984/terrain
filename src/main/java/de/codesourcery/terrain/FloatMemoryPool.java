package de.codesourcery.terrain;

import com.badlogic.gdx.utils.Disposable;
import com.sun.jna.Memory;
import com.sun.jna.Native;

import java.util.Iterator;
import java.util.LinkedList;

public class FloatMemoryPool implements Disposable
{
    private final LinkedList<PoolEntry> free = new LinkedList<>();
    private final LinkedList<PoolEntry> used = new LinkedList<>();

    public void dispose() {
    }

    protected static final class PoolEntry
    {
        public final int size;
        public final Memory memory;

        public PoolEntry(int size) {
            this.size = size;
            this.memory = new Memory(size * Native.getNativeSize(Double.TYPE));
        }
    }

    public Memory allocFloatMemory(int elements) {

        PoolEntry existing = find( elements );
        if ( existing == null ) {
            existing = new PoolEntry( elements );
            used.add( existing );
        }
        return existing.memory;
    }

    public void freeFloatMemory(Memory memory) {

        for (Iterator<PoolEntry> iterator = used.iterator(); iterator.hasNext(); )
        {
            PoolEntry e = iterator.next();
            if ( e.memory == memory )
            {
                iterator.remove();
                insert(free,e);
                return;
            }
        }
        throw new IllegalStateException("Memory not found: "+memory);
    }

    private PoolEntry find(int size)
    {
        int increment = 0;
        int pivot = free.size()/2;
        do
        {
            final PoolEntry result = free.get( pivot );
            if ( result.size >= size )
            {
                free.remove(pivot);
                insert(used,result);
                return result;
            }
            increment = (free.size()-pivot)/2;
            pivot = pivot + increment;
        } while ( increment > 0 );
        return null;
    }

    private void insert(LinkedList<PoolEntry> list,PoolEntry e)
    {
        if ( ! list.isEmpty() )
        {
            int i = 0;
            for (Iterator<PoolEntry> iterator = list.iterator(); iterator.hasNext(); i++)
            {
                PoolEntry l = iterator.next();
                if ( e.size <= list.get( i ).size )
                {
                    list.add( i, e );
                    return;
                }
            }
        }
        list.add( e );
    }
}
