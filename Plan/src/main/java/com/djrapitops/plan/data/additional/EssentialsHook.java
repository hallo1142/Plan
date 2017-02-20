package main.java.com.djrapitops.plan.data.additional;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import main.java.com.djrapitops.plan.Plan;

/**
 *
 * @author Rsl1122
 */
public class EssentialsHook extends Hook {

    private final Plan plugin;
    private Essentials ess;
    private List<String> warps;

    /**
     * Hooks to Essentials plugin
     * @param plugin
     */
    public EssentialsHook(Plan plugin) {
        super(Essentials.class);
        this.plugin = plugin;        
    }

    /**
     * Grabs information not provided by Player class or Plan from Essentials.
     * isEnabled() should be called before this method.
     *
     * @param uuid UUID of player
     * @return HashMap with boolean, int and string values: JAILED boolean, MUTED
     * boolean, AFK boolean, GOD boolean, JAILTIMEOUT
     */
    public HashMap<String, Serializable> getEssentialsData(UUID uuid) {
        HashMap<String, Serializable> essData = new HashMap<>();
        User user = ess.getUser(uuid);        
        essData.put("JAILED", user.isJailed());
        essData.put("MUTED", user.isMuted());
        essData.put("AFK", user.isAfk());
        essData.put("GOD", user.isGodModeEnabled());
        return essData;
    }

    /**
     * @return
     */
    public List<String> getWarps() {
        return (ArrayList<String>) ess.getWarps().getList();
    }

}
