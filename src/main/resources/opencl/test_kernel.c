__kernel void flow(__global const float *height, __global const float *water, __global float *dst, __global const int *relNeighbourOffsets)
{
    dst[ get_global_id(0) ]= get_global_id(0);
}