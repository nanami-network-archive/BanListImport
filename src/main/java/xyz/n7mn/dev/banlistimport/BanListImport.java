package xyz.n7mn.dev.banlistimport;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.*;
import java.util.Enumeration;
import java.util.Set;
import java.util.UUID;

public final class BanListImport extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        try {
            boolean found = false;
            Enumeration<Driver> drivers = DriverManager.getDrivers();

            while (drivers.hasMoreElements()){
                Driver driver = drivers.nextElement();
                if (driver.equals(new com.mysql.cj.jdbc.Driver())){
                    found = true;
                    break;
                }
            }

            if (!found){
                DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
            }

            BanList list = Bukkit.getServer().getBanList(BanList.Type.NAME);
            Connection con = DriverManager.getConnection("jdbc:mysql://" + getConfig().getString("mysqlServer") + ":" + getConfig().getInt("mysqlPort") + "/" + getConfig().getString("mysqlDatabase") + getConfig().getString("mysqlOption"), getConfig().getString("mysqlUsername"), getConfig().getString("mysqlPassword"));

            OkHttpClient client = new OkHttpClient();

            Set<BanEntry> entryList = list.getBanEntries();
            getLogger().info(entryList.size()+"件 インポート開始！");
            for (BanEntry entry : entryList){
                int id = 0;
                // https://api.mojang.com/users/profiles/minecraft/7mi_chan
                Request request = new Request.Builder()
                        .url("https://api.mojang.com/users/profiles/minecraft/"+ entry.getTarget())
                        .build();

                Response response = client.newCall(request).execute();
                APIData data = new Gson().fromJson(response.body().string(), APIData.class);
                UUID userUUID = UUID.fromString(data.getId().replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]+)", "$1-$2-$3-$4-$5"));
                response.close();
                request = new Request.Builder()
                        .url("https://api.mojang.com/users/profiles/minecraft/"+ entry.getSource())
                        .build();
                response = client.newCall(request).execute();
                data = new Gson().fromJson(response.body().string(), APIData.class);
                UUID executeUUID = UUID.fromString(data.getId().replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]+)", "$1-$2-$3-$4-$5"));
                response.close();

                PreparedStatement statement1 = con.prepareStatement("SELECT * FROM BanList ORDER BY BanID DESC");
                ResultSet set = statement1.executeQuery();
                if (set.next()){
                    id = set.getInt("BanID");
                }
                set.close();
                statement1.close();
                id++;

                PreparedStatement statement2 = con.prepareStatement("INSERT INTO `BanList`(`BanID`, `UserUUID`, `Reason`, `Area`, `IP`, `EndDate`, `ExecuteDate`, `ExecuteUserUUID`, `Active`) VALUES (?,?,?,?,?,?,?,?,?)");
                statement2.setInt(1, id);
                statement2.setString(2, userUUID.toString());
                statement2.setString(3, entry.getReason());
                statement2.setString(4, "all");
                statement2.setString(5, "");
                statement2.setString(6, "9999-12-31 23:59:59");
                statement2.setTimestamp(7, new Timestamp(entry.getCreated().getTime()));
                statement2.setString(8, executeUUID.toString());
                statement2.setBoolean(9, true);

                statement2.execute();
                statement2.close();

                getLogger().info("ID "+id+"を追加しました！");
            }
            getLogger().info("完了しました！");

            con.close();
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }


        Bukkit.getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
