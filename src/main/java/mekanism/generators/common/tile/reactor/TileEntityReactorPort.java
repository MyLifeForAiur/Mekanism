package mekanism.generators.common.tile.reactor;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.EnumSet;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.IHeatTransfer;
import mekanism.api.Range4D;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.ITubeConnection;
import mekanism.api.reactor.IReactorBlock;
import mekanism.api.util.CapabilityUtils;
import mekanism.common.Mekanism;
import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.CableUtils;
import mekanism.common.util.HeatUtils;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import mekanism.generators.common.item.ItemHohlraum;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TileEntityReactorPort extends TileEntityReactorBlock implements IFluidHandlerWrapper, IGasHandler, ITubeConnection, IHeatTransfer, IConfigurable
{
	public boolean fluidEject;
	
	public TileEntityReactorPort()
	{
		super("name", 1);
		
		inventory = new ItemStack[0];
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);

		fluidEject = nbtTags.getBoolean("fluidEject");
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);

		nbtTags.setBoolean("fluidEject", fluidEject);
		
		return nbtTags;
	}

	@Override
	public boolean isFrame()
	{
		return false;
	}

	@Override
	public void onUpdate()
	{
		if(changed)
		{
			worldObj.notifyNeighborsOfStateChange(getPos(), getBlockType());
		}
		
		super.onUpdate();

		if(!worldObj.isRemote)
		{
			CableUtils.emit(this);
			
			if(fluidEject && getReactor() != null && getReactor().getSteamTank().getFluid() != null)
			{
				IFluidTank tank = getReactor().getSteamTank();
				
				for(EnumFacing side : EnumFacing.values())
				{
					TileEntity tile = Coord4D.get(this).offset(side).getTileEntity(worldObj);

					if(tile != null && !(tile instanceof TileEntityReactorPort) && CapabilityUtils.hasCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite()))
					{
						IFluidHandler handler = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
						
						if(PipeUtils.canFill(handler, tank.getFluid()))
						{
							tank.drain(handler.fill(tank.getFluid(), true), true);
						}
					}
				}
			}
		}
	}

	@Override
	public int fill(EnumFacing from, FluidStack resource, boolean doFill)
	{
		if(resource.getFluid() == FluidRegistry.WATER && getReactor() != null && !fluidEject)
		{
			return getReactor().getWaterTank().fill(resource, doFill);
		}
		
		return 0;
	}

	@Override
	public FluidStack drain(EnumFacing from, FluidStack resource, boolean doDrain)
	{
		if(resource.getFluid() == FluidRegistry.getFluid("steam") && getReactor() != null)
		{
			getReactor().getSteamTank().drain(resource.amount, doDrain);
		}
		
		return null;
	}

	@Override
	public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain)
	{
		if(getReactor() != null)
		{
			return getReactor().getSteamTank().drain(maxDrain, doDrain);
		}
		
		return null;
	}

	@Override
	public boolean canFill(EnumFacing from, Fluid fluid)
	{
		return (getReactor() != null && fluid == FluidRegistry.WATER && !fluidEject);
	}

	@Override
	public boolean canDrain(EnumFacing from, Fluid fluid)
	{
		return (getReactor() != null && fluid == FluidRegistry.getFluid("steam"));
	}

	@Override
	public FluidTankInfo[] getTankInfo(EnumFacing from)
	{
		if(getReactor() == null)
		{
			return PipeUtils.EMPTY;
		}
		
		return new FluidTankInfo[] {getReactor().getWaterTank().getInfo(), getReactor().getSteamTank().getInfo()};
	}

	@Override
	public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer)
	{
		if(getReactor() != null)
		{
			if(stack.getGas() == GasRegistry.getGas("deuterium"))
			{
				return getReactor().getDeuteriumTank().receive(stack, doTransfer);
			}
			else if(stack.getGas() == GasRegistry.getGas("tritium"))
			{
				return getReactor().getTritiumTank().receive(stack, doTransfer);
			}
			else if(stack.getGas() == GasRegistry.getGas("fusionFuelDT"))
			{
				return getReactor().getFuelTank().receive(stack, doTransfer);
			}
		}
		
		return 0;
	}

	@Override
	public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer)
	{
		if(getReactor() != null)
		{
			if(getReactor().getSteamTank().getFluidAmount() > 0)
			{
				return new GasStack(GasRegistry.getGas("steam"), getReactor().getSteamTank().drain(amount, doTransfer).amount);
			}
		}

		return null;
	}

	@Override
	public boolean canReceiveGas(EnumFacing side, Gas type)
	{
		return (type == GasRegistry.getGas("deuterium") || type == GasRegistry.getGas("tritium") || type == GasRegistry.getGas("fusionFuelDT"));
	}

	@Override
	public boolean canDrawGas(EnumFacing side, Gas type)
	{
		return (type == GasRegistry.getGas("steam"));
	}

	@Override
	public boolean canTubeConnect(EnumFacing side)
	{
		return getReactor() != null;
	}
	
	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing side)
	{
		return capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == Capabilities.TUBE_CONNECTION_CAPABILITY 
				|| capability == Capabilities.HEAT_TRANSFER_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
				|| capability == Capabilities.CONFIGURABLE_CAPABILITY || super.hasCapability(capability, side);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing side)
	{
		if(capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == Capabilities.TUBE_CONNECTION_CAPABILITY
				|| capability == Capabilities.HEAT_TRANSFER_CAPABILITY || capability == Capabilities.CONFIGURABLE_CAPABILITY)
		{
			return (T)this;
		}
		
		if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
		{
			return (T)new FluidHandlerWrapper(this, side);
		}
		
		return super.getCapability(capability, side);
	}

	@Override
	public boolean canOutputTo(EnumFacing side)
	{
		return true;
	}

	@Override
	public double getEnergy()
	{
		if(getReactor() == null)
		{
			return 0;
		}
		else {
			return getReactor().getBufferedEnergy();
		}
	}

	@Override
	public void setEnergy(double energy)
	{
		if(getReactor() != null)
		{
			getReactor().setBufferedEnergy(energy);
		}
	}

	@Override
	public double getMaxEnergy()
	{
		if(getReactor() == null)
		{
			return 0;
		}
		else {
			return getReactor().getBufferSize();
		}
	}

	@Override
	public EnumSet<EnumFacing> getOutputtingSides()
	{
		return EnumSet.allOf(EnumFacing.class);
	}

	@Override
	public EnumSet<EnumFacing> getConsumingSides()
	{
		return EnumSet.noneOf(EnumFacing.class);
	}

	@Override
	public double getMaxOutput()
	{
		return 1000000000;
	}

	@Override
	public double getTemp()
	{
		if(getReactor() != null)
		{
			return getReactor().getTemp();
		}
		
		return 0;
	}

	@Override
	public double getInverseConductionCoefficient()
	{
		return 5;
	}

	@Override
	public double getInsulationCoefficient(EnumFacing side)
	{
		if(getReactor() != null)
		{
			return getReactor().getInsulationCoefficient(side);
		}
		
		return 0;
	}

	@Override
	public void transferHeatTo(double heat)
	{
		if(getReactor() != null)
		{
			getReactor().transferHeatTo(heat);
		}
	}

	@Override
	public double[] simulateHeat()
	{
		return HeatUtils.simulate(this);
	}

	@Override
	public double applyTemperatureChange()
	{
		if(getReactor() != null)
		{
			return getReactor().applyTemperatureChange();
		}
		
		return 0;
	}

	@Override
	public boolean canConnectHeat(EnumFacing side)
	{
		return getReactor() != null;
	}

	@Override
	public IHeatTransfer getAdjacent(EnumFacing side)
	{
		TileEntity adj = Coord4D.get(this).offset(side).getTileEntity(worldObj);
		
		if(CapabilityUtils.hasCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite()))
		{
			if(!(adj instanceof IReactorBlock))
			{
				return CapabilityUtils.getCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite());
			}
		}
		
		return null;
	}
	
	@Override
	public ItemStack getStackInSlot(int slotID)
	{
		return getReactor() != null && getReactor().isFormed() ? getReactor().getInventory()[slotID] : null;
	}
	
	@Override
	public int getSizeInventory()
	{
		return getReactor() != null && getReactor().isFormed() ? 1 : 0;
	}

	@Override
	public void setInventorySlotContents(int slotID, ItemStack itemstack)
	{
		if(getReactor() != null && getReactor().isFormed())
		{
			getReactor().getInventory()[slotID] = itemstack;

			if(itemstack != null && itemstack.stackSize > getInventoryStackLimit())
			{
				itemstack.stackSize = getInventoryStackLimit();
			}
		}
	}
	
	@Override
	public int[] getSlotsForFace(EnumFacing side)
	{
		return getReactor() != null && getReactor().isFormed() ? new int[] {0} : InventoryUtils.EMPTY;
	}
	
	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(getReactor() != null && getReactor().isFormed() && itemstack.getItem() instanceof ItemHohlraum)
		{
			ItemHohlraum hohlraum = (ItemHohlraum)itemstack.getItem();
			
			return hohlraum.getGas(itemstack) != null && hohlraum.getGas(itemstack).amount == hohlraum.getMaxGas(itemstack);
		}
		
		return false;
	}
	
	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, EnumFacing side)
	{
		if(getReactor() != null && getReactor().isFormed() && itemstack.getItem() instanceof ItemHohlraum)
		{
			ItemHohlraum hohlraum = (ItemHohlraum)itemstack.getItem();
			
			return hohlraum.getGas(itemstack) == null;
		}
		
		return false;
	}
	
	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);
		
		if(FMLCommonHandler.instance().getEffectiveSide().isClient())
		{
			boolean prevEject = fluidEject;
			fluidEject = dataStream.readBoolean();
			
			if(prevEject != fluidEject)
			{
				MekanismUtils.updateBlock(worldObj, getPos());
			}
		}
	}

	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		
		data.add(fluidEject);
		
		return data;
	}

	@Override
	public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side)
	{
		if(!worldObj.isRemote)
		{
			fluidEject = !fluidEject;
			String modeText = " " + (fluidEject ? EnumColor.DARK_RED : EnumColor.DARK_GREEN) + LangUtils.transOutputInput(fluidEject) + ".";
			player.addChatMessage(new TextComponentString(EnumColor.DARK_BLUE + "[Mekanism] " + EnumColor.GREY + LangUtils.localize("tooltip.configurator.reactorPortEject") + modeText));
			
			Mekanism.packetHandler.sendToReceivers(new TileEntityMessage(Coord4D.get(this), getNetworkedData(new ArrayList())), new Range4D(Coord4D.get(this)));
			markDirty();
		}
		
		return EnumActionResult.SUCCESS;
	}

	@Override
	public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side)
	{
		return EnumActionResult.PASS;
	}
}