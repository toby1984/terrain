        
        #define EPSILON 0.001
        
        __kernel void flow(__global const float *height, __global const float *water, __global float *dst, __constant const int *relNeighbourOffsets)
        {
            int ptr = get_global_id(0);
            
            float currentWater = water[ptr];
            if ( currentWater == 0 ) {
                dst[ptr] = ptr;
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
                dst[ptr] -= newValue < EPSILON ? currentWater : excessWater;
                
                for (int idx = 0 ; idx < 8 ; idx++)
                {
                    int offset = ptr + relNeighbourOffsets[idx];
                    float otherHeight = water[offset]+height[offset];
                    if ( otherHeight < currentHeight )
                    {
                        dst[offset] += fraction;
                    }
                }
            }
        }