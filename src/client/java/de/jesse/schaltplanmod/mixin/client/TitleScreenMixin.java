package de.jesse.schaltplanmod.mixin.client;

import de.jesse.schaltplanmod.client.RedstoneWorldScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void schaltplanmod$addRedstoneWorldButton(CallbackInfo info) {
		addRenderableWidget(Button.builder(Component.literal("Redstone World"), button -> {
					minecraft.setScreen(new RedstoneWorldScreen(this));
				})
				.bounds(width / 2 + 104, height / 4 + 48, 96, 20)
				.build());
	}
}
