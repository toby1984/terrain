#include "flow.h"
    
void flowRepeat(int size,float height[],float water[],int repeat) {
    for ( int i = 0 ; i < repeat ; i++) {
      flow(size,height,water);
    }
} 

void flow(int size,float height[],float water[]) 
{

    // relative offsets to direct neightbours of current cell
    int relNeighbourOffsets[] = {-size-1,-size,-size+1,-1,1,size-1,size,size+1};

    // array holding list of direct
    // neighbours whose level (water+height) is
    // below the current node's level (water+height)
    // so water needs to be re-distributed there
    int neighbours[8];
    
    // TODO: Code currently cheats and ignores the border area as
    // TODO: we'd need to do lots of additional comparisons to detect
    // TODO: those boundary cases (OR duplicate the loop and
    // TODO: deal with the first/last row/column separately)
    for (int y = 1; y < size-1 ; y++)
    {
        int ptr = y*size+1;
        for ( int x = 1 ; x < size-1 ; x++,ptr++ )
        {
            float currentWater = water[ptr];
            if ( currentWater == 0 ) {
                // no water in this cell
                continue;
            }
            // true height (ground height + water height)
            float currentHeight = currentWater + height[ptr];
            int pointCount = 0;
            float heightSum = 0;
            for (int j = 0 ; j < 8 ; j++ )
            {
                int relOffset = relNeighbourOffsets[j];
                int offset = ptr + relOffset;
                float otherHeight = water[offset]+height[offset];
                if ( otherHeight < currentHeight )
                {
                    // ok, downstream
                    heightSum += otherHeight;
                    neighbours[pointCount++] = offset;
                }
            }

            if ( pointCount > 0 )
            {
                float avgHeight = heightSum / pointCount;
                float h = currentHeight - avgHeight;
                float excessWater = currentWater < h ? currentWater : h;

                float fraction = excessWater / pointCount;
                float newValue = currentWater - fraction*pointCount;
                water[ptr] = newValue < EPSILON ? 0 : newValue;
                for ( int i = pointCount-1 ; i >= 0 ; i-- ) {
                    int offset = neighbours[i];
                    float newW = water[offset]+fraction;
                    water[offset] = newW;
                }
            }
        }
    }
}
