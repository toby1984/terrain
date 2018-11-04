package de.codesourcery.terrain;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class PerformanceTest
{
    @Test
    public void testPerformance() throws IOException
    {
        final Data data;
        try ( InputStream in = getClass().getResourceAsStream( "/test.s" ) )
        {
            if ( in == null ) {
                throw new FileNotFoundException( "Failed to load test data" );
            }
            data = Data.read( in );
            data.initWater( 1, 10 );
        }

        long start = System.currentTimeMillis();
        for ( int i = 0 ; i < 500 ; i++ )
        {
            data.flow(10);
        }
        long end = System.currentTimeMillis();
        System.out.println("1000 iterations took "+(end-start));
    }
}
