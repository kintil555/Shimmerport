package com.lowdragmc.shimmer.config;

import com.lowdragmc.shimmer.ShimmerConstants;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

import java.util.Objects;

interface FluidChecker extends Check {

	String getFluidName();

	default Pair<Identifier, Fluid> fluid() {
		var fluidName = getFluidName();
		Objects.requireNonNull(fluidName);
		if (!Identifier.isValid(fluidName)) {
			ShimmerConstants.LOGGER.error("invalid fluid name " + fluidName + " form" + getConfigSource());
			return null;
		}
		var fluidLocation = new Identifier(fluidName);
		if (!BuiltInRegistries.FLUID.containsKey(fluidLocation)) {
			ShimmerConstants.LOGGER.error("can't find fluid " + fluidLocation + " from" + getConfigSource());
			return Pair.of(fluidLocation, null);
		}
		Fluid fluid = BuiltInRegistries.FLUID.get(fluidLocation);
		return Pair.of(fluidLocation, fluid);
	}
}