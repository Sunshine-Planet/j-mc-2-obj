/*******************************************************************************
 * Copyright (c) 2012
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 ******************************************************************************/
package org.jmc.threading;

import org.jmc.*;
import org.jmc.geom.*;
import org.jmc.models.None;
import org.jmc.registry.NamespaceID;
import org.jmc.util.Log;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * ChunkProcessor reads through the given chunk and outputs faces
 */
public class UsdChunkProcessor {
	ObjChunkProcessor objProc = new ObjChunkProcessor(){
		/**
		 * Add a face with the given vertices to the chunk output.
		 *
		 * @param verts vertices of the face
		 * @param norms normals of the face
		 * @param uv texture coordinates for the vertices. If null, the default coordinates will be used
		 * (only accepted if face is a quad!).
		 * @param trans Transform to apply to the vertex coordinates. If null, no transform is applied
		 * @param tex Name of the material for the face
		 * @param canOptimise Allows this face to be optimised
		 */
		@Override
		public void addFace(Vertex[] verts, Vertex[] norms, UV[] uv, Transform trans, NamespaceID tex, boolean canOptimise) {
			super.addFace(verts, norms, uv, trans, tex, false);
		}
	};
	
	/**
	 * Returns all blocks from the given chunk buffer into the output.
	 */
	public ThreadUsdOutputQueue.ChunkOutput process(ThreadChunkDeligate chunk, Point chunkCoord)
	{
		int chunkX = chunkCoord.x;
		int chunkZ = chunkCoord.y;
		Map<String, ArrayList<FaceUtils.Face>> usdModels = new HashMap<>();
		ArrayList<BlockDataPos> usdBlocks = new ArrayList<>();
		ThreadUsdOutputQueue.ChunkOutput out = new ThreadUsdOutputQueue.ChunkOutput(chunkCoord, usdBlocks, usdModels);
		int xmin,xmax,ymin,ymax,zmin,zmax;
		Rectangle xy,xz;
		xy=chunk.getXYBoundaries();
		xz=chunk.getXZBoundaries();
		xmin=xy.x;
		xmax=xmin+xy.width;
		ymin=xy.y;
		ymax=ymin+xy.height;
		zmin=xz.y;
		zmax=zmin+xz.height;

		int xs=chunkX*16;
		int zs=chunkZ*16;
		int xe=xs+16;
		int ze=zs+16;

		if(xs<xmin) xs=xmin;
		if(xe>xmax) xe=xmax;
		if(zs<zmin) zs=zmin;
		if(ze>zmax) ze=zmax;

		for(int z = zs; z < ze; z++)
		{
			for(int x = xs; x < xe; x++)
			{
				for(int y = ymin; y < ymax; y++)
				{
					BlockData block=chunk.getBlockData(x, y, z);
					NamespaceID blockBiome=chunk.getBlockBiome(x, y, z);
					
					if(block == null || block.id == NamespaceID.NULL)
						continue;
					
					if(Options.excludeBlocks.contains(block.id))
						continue;
					
					BlockInfo blockInfo = BlockTypes.get(block);
					
					if(Options.convertOres) {
						NamespaceID oreBase = blockInfo.getOreBase();
						if (oreBase != null) {
							block.id = oreBase;
							blockInfo = BlockTypes.get(block);
						}
					}
					
					try {
						if (blockInfo.getModel().getClass() != None.class) {
							if (!usdModels.containsKey(block.toFullIdString())) {
								blockInfo.getModel().addModel(objProc, chunk, x, y, z, block, blockBiome);
								if (Boolean.parseBoolean(block.state.get("waterlogged"))) {
									BlockTypes.get(new BlockData(new NamespaceID("minecraft", "water"))).getModel().addModel(objProc, chunk, x, y, z, block, blockBiome);
								}
								ArrayList<FaceUtils.Face> faces = new ArrayList<>(objProc.faces.size());
								for (FaceUtils.Face face : objProc.faces) {
									faces.add(Transform.translation(-x, -y, -z).multiply(face));
								}
								usdModels.put(block.toHashedId(), faces);
							}
							usdBlocks.add(new BlockDataPos(block, new BlockPos(x, y, z), blockBiome));
						}
					} catch (Exception ex) {
						Log.errorOnce(String.format("Error rendering block '%s', skipping.", block.id), ex, true);
					} finally {
						objProc.faces.clear();
					}
				}
			}
		}
		
//
//		if (Options.renderEntities) {
//			for (TAG_Compound entity:chunk.getEntities(chunk_x, chunk_z)) {
//				if (entity == null) continue;
//				Entity handler=EntityTypes.getEntity(entity);
//				if (handler!=null) {
//					try {
//						Vertex pos = handler.getPosition(entity);
//						if (pos.x > xmin && pos.y > ymin && pos.z > zmin && pos.x < xmax && pos.y < ymax && pos.z < zmax) {
//							handler.addEntity(this, entity);
//						}
//					} catch (Exception ex) {
//						Log.error(String.format("Error rendering entity %s, skipping.", handler.id), ex);
//					}
//				}
//			}
//
//			for (TAG_Compound entity:chunk.getTileEntities(chunk_x, chunk_z)) {
//				if (entity == null) continue;
//				Entity handler=EntityTypes.getEntity(entity);
//				if(handler!=null) {
//					try {
//						Vertex pos = handler.getPosition(entity);
//						if (pos.x > xmin && pos.y > ymin && pos.z > zmin && pos.x < xmax && pos.y < ymax && pos.z < zmax) {
//							handler.addEntity(this, entity);
//						}
//					}
//					catch (Exception ex) {
//						Log.error("Error rendering tile entity, skipping.", ex);
//					}
//				}
//			}
//		}
		
		return out;
	}
}
