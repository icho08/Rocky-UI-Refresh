package dev.i726.rocky.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Frustum.class)
public interface FrustumAccessor {

	@Accessor
	FrustumIntersection getIntersection();

	@Accessor
	void setIntersection(FrustumIntersection vector4f);

	@Accessor("camX")
	double getX();

	@Accessor("camX")
	void setX(double x);

	@Accessor("camY")
	double getY();

	@Accessor("camY")
	void setY(double y);

	@Accessor("camZ")
	double getZ();

	@Accessor("camZ")
	void setZ(double z);
}
