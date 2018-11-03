CC=gcc
CFLAGS=-I.


flow:	flow.c flow.h
	gcc -O3 -shared -fpic -o lib/libflow.so flow.c 

# init:	
# 	mkdir -p target/linux-x86-64
