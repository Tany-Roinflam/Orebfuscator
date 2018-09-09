/**
 * @author lishid
 * @author Aleksey Terzi
 *
 */

package com.lishid.orebfuscator.nms.v1_13_R2;

import net.minecraft.server.v1_13_R2.Item;
import net.minecraft.server.v1_13_R2.Block;
import net.minecraft.server.v1_13_R2.BlockPosition;
import net.minecraft.server.v1_13_R2.Chunk;
import net.minecraft.server.v1_13_R2.ChunkProviderServer;
import net.minecraft.server.v1_13_R2.IBlockData;
import net.minecraft.server.v1_13_R2.IChatBaseComponent;
import net.minecraft.server.v1_13_R2.Packet;
import net.minecraft.server.v1_13_R2.TileEntity;
import net.minecraft.server.v1_13_R2.WorldServer;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftChatMessage;
import org.bukkit.entity.Player;

import com.lishid.orebfuscator.nms.IBlockInfo;
import com.lishid.orebfuscator.nms.IChunkCache;
import com.lishid.orebfuscator.nms.INBT;
import com.lishid.orebfuscator.nms.INmsManager;
import com.lishid.orebfuscator.types.BlockCoord;
import com.lishid.orebfuscator.types.BlockState;

import java.util.HashMap;

public class NmsManager implements INmsManager {
	private int maxLoadedCacheFiles;
	private HashMap<Integer, BlockState> precalculatedBlockStates = new HashMap<>();
	
	public void setMaxLoadedCacheFiles(int value) {
		this.maxLoadedCacheFiles = value;
	}
	
	public INBT createNBT() {
		return new NBT();
	}

	@Override
	public void preCalculateBlockStates() {
		Block.REGISTRY_ID.iterator().forEachRemaining(iBlockData -> {
			BlockState bs = new BlockState();
			bs.id = Block.REGISTRY_ID.getId(iBlockData);
			CraftBlockData cBlock = CraftBlockData.fromData(iBlockData);
			bs.type = cBlock.getMaterial();

			precalculatedBlockStates.put(bs.id, bs);
		});
	}

	public BlockState getBlockStateForIdPopulatingCache(int id) {
		BlockState bs = new BlockState();
		bs.id = id;

		IBlockData block = Block.getByCombinedId(id);
		CraftBlockData cBlock = CraftBlockData.fromData(block);
		bs.type = cBlock.getMaterial();

		precalculatedBlockStates.put(bs.id, bs);
		return bs;
	}
	
	@Override
	public IChunkCache createChunkCache() {
		return new ChunkCache(this.maxLoadedCacheFiles);
	}
	
	@Override
    public void updateBlockTileEntity(BlockCoord blockCoord, Player player) {
        CraftWorld world = (CraftWorld)player.getWorld();
        TileEntity tileEntity = world.getTileEntityAt(blockCoord.x, blockCoord.y, blockCoord.z);
        
        if (tileEntity == null) {
            return;
        }
        
        Packet<?> packet = tileEntity.getUpdatePacket();
        
        if (packet != null) {
            CraftPlayer player2 = (CraftPlayer)player;
            player2.getHandle().playerConnection.sendPacket(packet);
        }
    }

	@Override
    public void notifyBlockChange(World world, IBlockInfo blockInfo) {
    	BlockPosition blockPosition = new BlockPosition(blockInfo.getX(), blockInfo.getY(), blockInfo.getZ());
    	IBlockData blockData = ((BlockInfo)blockInfo).getBlockData();
    	
        ((CraftWorld)world).getHandle().notify(blockPosition, blockData, blockData, 0);
    }
    
	@Override
    public int getBlockLightLevel(World world, int x, int y, int z) {
		return ((CraftWorld)world).getHandle().getLightLevel(new BlockPosition(x, y, z));
    }

	@Override
	public BlockState getBlockStateFromId(int id) {
		if (precalculatedBlockStates.containsKey(id)) {
			return precalculatedBlockStates.get(id);
		} else {
			return getBlockStateForIdPopulatingCache(id);
		}
	}
	
	@Override
	public void setBlockStateFromMaterial(Material type, BlockState blockState) {
		blockState.type = type;
		if (type.isBlock()) {
			Block block = CraftMagicNumbers.getBlock(type);
			blockState.id = Block.getCombinedId(block.getBlockData());
		} else {
			Item item = CraftMagicNumbers.getItem(type);
			blockState.id = Item.getId(item);
		}

	}
    
	@Override
	public IBlockInfo getBlockInfo(World world, int x, int y, int z) {
		IBlockData blockData = getBlockData(world, x, y, z, false);
		
		return blockData != null
				? new BlockInfo(x, y, z, blockData)
				: null;
	}
	
	@Override
	public BlockState getBlockState(World world, int x, int y, int z) {
		IBlockData blockData = getBlockData(world, x, y, z, false);
		
		if(blockData == null) return null;
		
		//Block block = blockData.getBlock();
		
		BlockState blockState = new BlockState();
		blockState.type = world.getBlockAt(x, y, z).getType();
		blockState.id = Block.getCombinedId(blockData);
		//blockState.meta = block.toLegacyData(blockData);
		
		return blockState;
	}
	
	@Override
	public int getBlockId(World world, int x, int y, int z) {
		IBlockData blockData = getBlockData(world, x, y, z, false);
		return blockData != null ? Block.getCombinedId(blockData): -1;
	}
	
	@Override
	public BlockData getBlockDataFromBlockState(BlockState blockState) {
		IBlockData block = Block.getByCombinedId(blockState.id);
		CraftBlockData cBlock = CraftBlockData.fromData(block); 		
		return cBlock;
	}

	@Override
	public int loadChunkAndGetBlockId(World world, int x, int y, int z) {
		IBlockData blockData = getBlockData(world, x, y, z, true);
		return blockData != null ? Block.getCombinedId(blockData): -1;
	}
	
	@Override
	public String getTextFromChatComponent(String json) {
		IChatBaseComponent component = IChatBaseComponent.ChatSerializer.a(json);
		return CraftChatMessage.fromComponent(component);
	}
	
	private static IBlockData getBlockData(World world, int x, int y, int z, boolean loadChunk) {
		int chunkX = x >> 4;
		int chunkZ = z >> 4;

		WorldServer worldServer = ((CraftWorld)world).getHandle();
		ChunkProviderServer chunkProviderServer = worldServer.getChunkProviderServer();
		
		if(!loadChunk && !chunkProviderServer.isLoaded(chunkX, chunkZ)) return null;
		
		Chunk chunk = chunkProviderServer.getChunkAt(chunkX, chunkZ, true, true);
		
		return chunk != null ? chunk.getType(new BlockPosition(x, y, z)) : null;
	}
}