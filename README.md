# terrain

This is a small toy project where I actually wanted to play around with water-based 
terrain erosion to get more realistic heightmap than just using Simplex/Perlin noise or midpoint displacement alone. 
I then got distracted by an interesting side-problem, creating lakes/ocean from water that follows the terrain's height gradient 
downwards.
While the algorithm is straight-forward I got curious how fast I could make the implementation.

1. Attempt: I started out with a very simple but slow Java implementation
2. Attempt: I streamlined the implementation as much as possible, even taking shortcuts like ignoring the heightmap's border area so I 
wouldn't have to deal with boundary checks there
3. Attempt: Still not exactly fast, I converted the algorithm to C, compiled it with gcc -O3 and used JNA to invoke it from Java
4. Attempt: The C code is roughly 20-25% faster than the pure Java implementation but still not exactly blazingly fast...
5. Attempt: I re-wrote the Java code to run multi-threaded ; scales linearly with the number of CPU cores but there *has* to 
be an even faster way
6. Attempt: WIP - I'll try to use a OpenGL shader/OpenCL but first I have to get all the tedious setup code right....

Lessons learned so far:

- JNA performance with Java arrays sucks, using NIO buffers is way faster
- code that does a lot of array accesses is definitely slowed down by JVM array bounds checking (as the compiler cannot elide the checks in my code)
- using a FloatBuffer instead of float[] is vastly faster (I blame this on the array bounds checking but maybe this is really because FloatBuffer#set()/#get() gets compiled using intrinsics)
- using a one-dimensional array instead of a two-dimensional one (and calculating offsets by doing y*width+x) is faster by
quite some margin, probably because of better cache locality and less array-bounds checking. Also really convenient if you frequently need to iterate over all elements in the array.
