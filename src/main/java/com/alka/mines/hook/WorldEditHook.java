package com.alka.mines.hook;

import com.alka.mines.model.MineRegion;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Captura a selecao ativa do jogador no WorldEdit/FAWE (comandos //pos1, //pos2, //wand). */
public class WorldEditHook {

    public Optional<MineRegion> getSelection(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));

        try {
            Region region = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            return Optional.of(new MineRegion(player.getWorld().getName(),
                    min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z()));
        } catch (IncompleteRegionException e) {
            return Optional.empty();
        }
    }
}
