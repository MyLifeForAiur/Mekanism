package mekanism.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.content.transporter.TransporterManager;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;

import org.apache.commons.lang3.tuple.Pair;

public class InventoryNetwork extends DynamicNetwork<IInventory, InventoryNetwork>
{
	public InventoryNetwork() {}

	public InventoryNetwork(Collection<InventoryNetwork> networks)
	{
		for(InventoryNetwork net : networks)
		{
			if(net != null)
			{
				adoptTransmittersAndAcceptorsFrom(net);
				net.deregister();
			}
		}

		register();
	}
	
	public List<AcceptorData> calculateAcceptors(ItemStack stack, EnumColor color)
	{
		List<AcceptorData> toReturn = new ArrayList<AcceptorData>();
		
		for(Coord4D coord : possibleAcceptors.keySet())
		{
			if(coord == null)
			{
				continue;
			}
			
			EnumSet<EnumFacing> sides = acceptorDirections.get(coord);
			IInventory acceptor = (IInventory)coord.getTileEntity(getWorld());
			
			if(sides == null || sides.isEmpty())
			{
				continue;
			}
			
			AcceptorData data = null;
			
			for(EnumFacing side : sides)
			{
				ItemStack returned = TransporterManager.getPredictedInsert((TileEntity)acceptor, color, stack, side.getOpposite());
				
				if(TransporterManager.didEmit(stack, returned))
				{
					if(data == null)
					{
						data = new AcceptorData(coord, returned, side.getOpposite());
					}
					else {
						data.sides.add(side.getOpposite());
					}
				}
			}
			
			if(data != null)
			{
				toReturn.add(data);
			}
		}
		
		return toReturn;
	}
	
	public static class AcceptorData
	{
		public Coord4D location;
		public ItemStack rejected;
		public EnumSet<EnumFacing> sides = EnumSet.noneOf(EnumFacing.class);
		
		public AcceptorData(Coord4D coord, ItemStack stack, EnumFacing side)
		{
			location = coord;
			rejected = stack;
			sides.add(side);
		}
	}

	@Override
	public void onUpdate()
	{
		super.onUpdate();

		if(FMLCommonHandler.instance().getEffectiveSide().isServer())
		{
			//Future!
		}
	}

	@Override
	public void absorbBuffer(IGridTransmitter<IInventory, InventoryNetwork> transmitter) {}

	@Override
	public void clampBuffer() {}

	@Override
	public Set<Pair<Coord4D, IInventory>> getAcceptors(Object data)
	{
		Set<Pair<Coord4D, IInventory>> toReturn = new HashSet<>();
		
		if(FMLCommonHandler.instance().getEffectiveSide().isClient())
		{
			return toReturn;
		}
		
		return toReturn;
	}

	@Override
	public String toString()
	{
		return "[InventoryNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
	}

	@Override
	public String getNeededInfo()
	{
		return null;
	}

	@Override
	public String getStoredInfo()
	{
		return null;
	}

	@Override
	public String getFlowInfo()
	{
		return null;
	}
}
