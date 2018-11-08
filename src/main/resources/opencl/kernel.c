#define EPSILON 0.0001

__kernel void flow(__global const float *height, __global float *water,
__constant const int *relNeighbourOffsets, const int rowSize)
{
    int ptr = get_global_id(0)+1+rowSize;
    
    float currentWater = water[ptr];
    if ( currentWater == 0 ) {
        return;
    }
    // true height (ground height + water height)
    float currentHeight = currentWater + height[ptr];
    float heightSum = 0;
    int pointCount = 0;
    for (int idx = 0 ; idx < 8 ; idx++)
    {
        int offset = ptr + relNeighbourOffsets[idx];
        float otherHeight = water[offset]+height[offset];
        if ( otherHeight < currentHeight )
        {
            // ok, downstream
            heightSum += otherHeight;
            pointCount++;
        }
    }
    
    if ( pointCount != 0 )
    {
        float avgHeight = heightSum / pointCount;
        float h = currentHeight - avgHeight;
        float excessWater = currentWater < h ? currentWater : h; 
        
        float fraction = excessWater / pointCount;
        float newValue = currentWater - excessWater;
        water[ptr] = newValue < EPSILON ? 0 : newValue;

        if ( fraction > EPSILON )
        {
            for (int idx = 0 ; idx < 8 ; idx++)
            {
                int offset = ptr + relNeighbourOffsets[idx];
                float otherHeight = water[offset]+height[offset];
                if ( otherHeight < currentHeight )
                {
                    water[offset] += fraction;
                }
            }
        }
    }
}
