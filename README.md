# Bungeen
[![Bungee Cord 1.12.2](https://img.shields.io/badge/BungeeCord-1.12.2-brightgreen.svg)](https://www.spigotmc.org/wiki/bungeecord/)
master:[![master Build Status]( https://travis-ci.org/kubotan/Bungeen.svg?branch=master)](https://travis-ci.org/kubotan/Bungeen)
develop:[![Build Status]( https://travis-ci.org/kubotan/Bungeen.svg?branch=develop)](https://travis-ci.org/kubotan/Bungeen)
[![GitHub release](https://img.shields.io/github/release/kubotan/Bungeen.svg)](https://github.com/kubotan/Bungeen/releases)
[![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://github.com/kubotan/Bungeen/issues)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL%20v3-blue.svg)](https://github.com/kubotan/Bungeen/blob/master/LICENSE)

Bungeen is a plugin that connects to another Minecraft server by right-clicking a signboard.

# Installation method
Please put jar in the plugins folder.   
Please put it only in the plugin folder of spigot or bukkit.   

Note: It is recommended that you use a protection plug-in such as LWC or WorldGuard.  
Required plugin BungeeCord.  

# Useage
1. Create signbord.  
line1 `[bungeen]` Fixed character string  
line2 `hub` Specify server name to be used with `/server` command  
line3 `192.168.1.77` IP address used for status check  
line4 `25565` Port used for status check  

2. Reload.
Input the `/bungeen reload` command in the chat field and press the Enter key.

3. Other command.
```
/bungeen reload - Reload the configuration.
/bungeen setname [server name] [value] - Set the display value of the first line of the signboard.
/bungeen setcomment [server name] [value] - Set the display value of the last line of the signboard.
/bungeen setprotocoltype [server name] [default|legacy] - default:Client version 1.7 or later legecy:Other than default
/bungeen remove [server name] - Remove the setting of the specified server name.
/bungeen addmember [server name] [playername] - Everyone can pass before this command is executed.If more than one player is added, only those who are added can pass.
/bungeen delmember [server name] [playername] - Delete players permitted to pass through.Everyone can pass if everyone is deleted.
/bungeen delallmember [server name] - Delete all players permitted to pass through.Everyone can pass if everyone is deleted.
```

4. Configure(config.yml)
```
message:
  online: §1§lOnline
  offline: §8§lOffline
  blink: §1§lRight click!
  deny: §8§lAccess denied
  coolingdown: §8§lCooling down
setting:
  retrylimit: 3                # Maximum number of retries(Please adjust when it goes offline with stator check.)
  retryclearinterval: 86400    # If the number of retries for status check exceeds the limit, no status check is performed.
                               # However, when the number of seconds specified here is exceeded,
                               # the number of retries is reset to 0 times.
```

# Permission
Only the op can perform setting work.

# Disclaimer
Do not assume any responsibility by use. Please use it at your own risk.

# Other
A message is defined in the configuration file.
Please refer to the wiki for each language setting.
