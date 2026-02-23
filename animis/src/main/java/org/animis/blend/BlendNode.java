package org.animis.blend;

public sealed interface BlendNode permits ClipNode, LerpNode, AddNode, OneDNode, ProceduralNode {}
