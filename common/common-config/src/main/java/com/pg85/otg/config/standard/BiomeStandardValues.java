package com.pg85.otg.config.standard;

import com.pg85.otg.config.settingType.DoubleArraySetting;
import com.pg85.otg.config.settingType.MaterialSetting;
import com.pg85.otg.config.settingType.Setting;
import com.pg85.otg.config.settingType.Settings;
import com.pg85.otg.constants.SettingsEnums.GrassColorModifier;
import com.pg85.otg.constants.SettingsEnums.MineshaftType;
import com.pg85.otg.constants.SettingsEnums.OceanRuinsType;
import com.pg85.otg.constants.SettingsEnums.RareBuildingType;
import com.pg85.otg.constants.SettingsEnums.RuinedPortalType;
import com.pg85.otg.constants.SettingsEnums.VillageType;
import com.pg85.otg.util.biome.ReplacedBlocksMatrix;
import com.pg85.otg.util.biome.WeightedMobSpawnGroup;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BiomeStandardValues extends Settings
{
    // >> Biome Extensions & Related
    public static final Collection<String> BiomeConfigExtensions = Arrays.asList(
		"BiomeConfig.ini", 
		".biome", 
		".bc", 
		".bc.ini",
    	".biome.ini"
	);

    public static final Setting<Boolean>
    	RESOURCE_INHERITANCE = booleanSetting("ResourceInheritance", true),
    	USE_WORLD_WATER_LEVEL = booleanSetting("UseWorldWaterLevel", true),    	 
		DISABLE_BIOME_HEIGHT = booleanSetting("DisableBiomeHeight", false),
		STRONGHOLDS_ENABLED = booleanSetting("StrongholdsEnabled", true),
		NETHER_FORTRESSES_ENABLED = booleanSetting("NetherFortressesEnabled", false),
		OCEAN_MONUMENTS_ENABLED = booleanSetting("OceanMonumentsEnabled", false),
		WOODLAND_MANSIONS_ENABLED = booleanSetting("WoodlandMansionsEnabled", false),
		BURIED_TREASURE_ENABLED = booleanSetting("BuriedTreasureEnabled", false),
		SHIP_WRECK_ENABLED = booleanSetting("ShipWreckEnabled", false),
		SHIP_WRECK_BEACHED_ENABLED = booleanSetting("ShipWreckBeachedEnabled", false),
		PILLAGER_OUTPOST_ENABLED = booleanSetting("PillagerOutpostEnabled", false),
		BASTION_REMNANT_ENABLED = booleanSetting("BastionRemnantEnabled", false),
		NETHER_FOSSIL_ENABLED = booleanSetting("NetherFossilEnabled", false),
		END_CITY_ENABLED = booleanSetting("EndCityEnabled", false),
		REPLACE_CURRENT_MUSIC = booleanSetting("ReplaceCurrentMusic", false)
	;

    public static final Setting<String>
    	BIOME_EXTENDS = stringSetting("BiomeExtends", ""),
    	RIVER_BIOME = stringSetting("RiverBiome", "River"),
    	VANILLA_BIOME = stringSetting("ReplaceToBiomeName", ""),
    	BIOME_DICT_ID = stringSetting("BiomeDictId", ""),
    	INHERIT_MOBS_BIOME_NAME = stringSetting("InheritMobsBiomeName", ""),
    	PARTICLE_TYPE = stringSetting("ParticleType", ""),
		MUSIC = stringSetting("Music", ""),
		AMBIENT_SOUND = stringSetting("AmbientSound", ""),
		MOOD_SOUND = stringSetting("MoodSound", "minecraft:ambient.cave"),
		ADDITIONS_SOUND = stringSetting("AdditionsSound", "")
	;

    public static final Setting<Integer>
    	BIOME_SIZE = intSetting("BiomeSize", 5, 0, 20),
		BIOME_SIZE_WHEN_ISLE = intSetting("BiomeSizeWhenIsle", 5, 0, 20),
		BIOME_SIZE_WHEN_BORDER = intSetting("BiomeSizeWhenBorder", 5, 0, 20),
		BIOME_RARITY = intSetting("BiomeRarity", 100, 0, Integer.MAX_VALUE),
		BIOME_RARITY_WHEN_ISLE = intSetting("BiomeRarityWhenIsle", 100, 0, Integer.MAX_VALUE),
		SMOOTH_RADIUS = intSetting("SmoothRadius", 2, 0, 32),
		CUSTOM_HEIGHT_CONTROL_SMOOTH_RADIUS = intSetting("CustomHeightControlSmoothRadius", 2, 0, 32),
		WATER_LEVEL_MAX = WorldStandardValues.WATER_LEVEL_MAX,
		WATER_LEVEL_MIN = WorldStandardValues.WATER_LEVEL_MIN,
		VILLAGE_SIZE = intSetting("VillageSize", 6, 0, Integer.MAX_VALUE),
		PILLAGER_OUTPOST_SIZE = intSetting("PillagerOutpostSize", 7, 0, Integer.MAX_VALUE),
		BASTION_REMNANT_SIZE = intSetting("BastionRemnantSize", 6, 0, Integer.MAX_VALUE),
		MUSIC_MIN_DELAY = intSetting("MusicMinDelay", 0, 0, Integer.MAX_VALUE),
		MUSIC_MAX_DELAY = intSetting("MusicMaxDelay", 0, 0, Integer.MAX_VALUE),
		MOOD_SOUND_DELAY = intSetting("MoodSoundDelay", 6000, 0, Integer.MAX_VALUE),
		MOOD_SEARCH_RANGE = intSetting("MoodSearchRange", 8, 0, Integer.MAX_VALUE)
	;

    public static final Setting<Integer>
    	BIOME_COLOR = colorSetting("BiomeColor", "#FFFFFF"),
    	SKY_COLOR = colorSetting("SkyColor", "#7BA5FF"),
    	WATER_COLOR = colorSetting("WaterColor", "#FFFFFF"),
    	GRASS_COLOR = colorSetting("GrassColor", "#FFFFFF"),
    	FOLIAGE_COLOR = colorSetting("FoliageColor", "#FFFFFF"),
    	FOG_COLOR = colorSetting("FogColor", "#000000"),
		WATER_FOG_COLOR = colorSetting("WaterFogColor", "#000000")
    ;

    public static final Setting<List<String>>
    	ISLE_IN_BIOME = stringListSetting("IsleInBiome", "Ocean"),
    	BIOME_IS_BORDER = stringListSetting("BiomeIsBorder"),
    	NOT_BORDER_NEAR = stringListSetting("NotBorderNear")
    ;

    public static final Setting<Double>
    	VOLATILITY_1 = doubleSetting("Volatility1", 0, -1000, 1000),
    	VOLATILITY_2 = doubleSetting("Volatility2", 0, -1000, 1000),
    	VOLATILITY_WEIGHT_1 = doubleSetting("VolatilityWeight1", 0.5, -1000, 1000),
    	VOLATILITY_WEIGHT_2 = doubleSetting("VolatilityWeight2", 0.45, -1000, 1000),
    	MAX_AVERAGE_HEIGHT = doubleSetting("MaxAverageHeight", 0, -1000, 1000),
    	MAX_AVERAGE_DEPTH = doubleSetting("MaxAverageDepth", 0, -1000, 1000),
		MOOD_OFFSET = doubleSetting("MoodOffset", 2.0, 0, Double.MAX_VALUE),
		ADDITIONS_TICK_CHANCE = doubleSetting("AdditionsTickChance", 0, 0, Double.MAX_VALUE)
    ;

    public static final Setting<LocalMaterialData>
    	STONE_BLOCK = new MaterialSetting("StoneBlock", LocalMaterials.STONE_NAME),
    	SURFACE_BLOCK = new MaterialSetting("SurfaceBlock", LocalMaterials.GRASS_NAME),
    	GROUND_BLOCK = new MaterialSetting("GroundBlock", LocalMaterials.DIRT_NAME),
    	COOLED_LAVA_BLOCK = WorldStandardValues.COOLED_LAVA_BLOCK,
    	WATER_BLOCK = WorldStandardValues.WATER_BLOCK,
    	ICE_BLOCK = WorldStandardValues.ICE_BLOCK
    ;

    public static final Setting<double[]>
    	CUSTOM_HEIGHT_CONTROL = new DoubleArraySetting("CustomHeightControl")
    ;

    public static final Setting<Float>
    	BIOME_TEMPERATURE = floatSetting("BiomeTemperature", 0.5f, 0, 2),
    	BIOME_WETNESS = floatSetting("BiomeWetness", 0.5f, 0, 1),
    	BIOME_HEIGHT = floatSetting("BiomeHeight", 0.1f, -10, 10),
    	BIOME_VOLATILITY = floatSetting("BiomeVolatility", 0.3f, -1000, 1000),
    	// TODO: Find the proper max values for these probabilities, likely 1 for most.
    	MINESHAFT_PROBABILITY = floatSetting("MineshaftProbability", 0.004f, 0f, 1f),
		OCEAN_RUINS_LARGE_PROBABILITY = floatSetting("OceanRuinsLargeProbability", 0.3f, 0f, 1f),
		OCEAN_RUINS_CLUSTER_PROBABILITY = floatSetting("OceanRuinsClusterProbability", 0.9f, 0f, 1f),
		BURIED_TREASURE_PROBABILITY = floatSetting("BuriedTreasureProbability", 0.01f, 0f, 1f),
		PARTICLE_PROBABILITY = floatSetting("ParticleProbability", 0.118093334f, 0, 1f)
    ;

    public static final Setting<List<WeightedMobSpawnGroup>>
    	SPAWN_MONSTERS = mobGroupListSetting("SpawnMonsters"),
    	SPAWN_CREATURES = mobGroupListSetting("SpawnCreatures"),
    	SPAWN_WATER_CREATURES = mobGroupListSetting("SpawnWaterCreatures"),
    	SPAWN_AMBIENT_CREATURES = mobGroupListSetting("SpawnAmbientCreatures"),
		SPAWN_WATER_AMBIENT_CREATURES = mobGroupListSetting("SpawnWaterAmbientCreatures"),
		SPAWN_MISC_CREATURES = mobGroupListSetting("SpawnMiscCreatures")
    ;

    public static final Setting<VillageType> VILLAGE_TYPE = enumSetting("VillageType", VillageType.disabled);
    public static final Setting<MineshaftType> MINESHAFT_TYPE = enumSetting("MineshaftType", MineshaftType.normal);
    public static final Setting<RareBuildingType> RARE_BUILDING_TYPE = enumSetting("RareBuildingType", RareBuildingType.disabled);
    public static final Setting<RuinedPortalType> RUINED_PORTAL_TYPE = enumSetting("RuinedPortalType", RuinedPortalType.disabled);
    public static final Setting<OceanRuinsType> OCEAN_RUINS_TYPE = enumSetting("OceanRuinsType", OceanRuinsType.disabled);
    public static final Setting<GrassColorModifier> GRASS_COLOR_MODIFIER = enumSetting("GrassColorModifier", GrassColorModifier.None);    
    
    public static final Setting<ReplacedBlocksMatrix> REPLACED_BLOCKS = replacedBlocksSetting("ReplacedBlocks");

    // End biome settings

    // Resource settings
    public static final int SmallLakeWaterFrequency = 4;
    public static final int SmallLakeLavaFrequency = 2;
    public static final int SmallLakeWaterRarity = 7;
    public static final int SmallLakeLavaRarity = 1;
    public static final int SmallLakeMinAltitude = 8;
    public static final int SmallLakeMaxAltitude = 119;

    public static final int SmallLakeLavaFrequency2 = 2;
    public static final int SmallLakeLavaRarity2 = 8;
    public static final int SmallLakeMinAltitude2 = 6;
    public static final int SmallLakeMaxAltitude2 = 50;
    
    public static final int UndergroundLakeFrequency = 2;
    public static final int UndergroundLakeRarity = 5;
    public static final int UndergroundLakeMinSize = 50;
    public static final int UndergroundLakeMaxSize = 60;
    public static final int UndergroundLakeMinAltitude = 0;
    public static final int UndergroundLakeMaxAltitude = 49;

    public static final int DungeonRarity = 100;
    public static final int DungeonFrequency = 8;
    public static final int DungeonMinAltitude = 0;

    public static final int DirtDepositRarity = 100;
    public static final int DirtDepositFrequency = 10;
    public static final int DirtDepositSize = 33;
    public static final int DirtDepositMinAltitude = 0;
    public static final int DirtDepositMaxAltitude = 255;

    public static final int GravelDepositRarity = 100;
    public static final int GravelDepositFrequency = 8;
    public static final int GravelDepositSize = 33;
    public static final int GravelDepositMinAltitude = 0;
    public static final int GravelDepositMaxAltitude = 255;

    public static final int GraniteDepositRarity = 100;
    public static final int GraniteDepositFrequency = 10;
    public static final int GraniteDepositSize = 33;
    public static final int GraniteDepositMinAltitude = 0;
    public static final int GraniteDepositMaxAltitude = 79;

    public static final int DioriteDepositRarity = 100;
    public static final int DioriteDepositFrequency = 10;
    public static final int DioriteDepositSize = 33;
    public static final int DioriteDepositMinAltitude = 0;
    public static final int DioriteDepositMaxAltitude = 79;

    public static final int AndesiteDepositRarity = 100;
    public static final int AndesiteDepositFrequency = 10;
    public static final int AndesiteDepositSize = 33;
    public static final int AndesiteDepositMinAltitude = 0;
    public static final int AndesiteDepositMaxAltitude = 79;

    public static final int CoalDepositRarity = 100;
    public static final int CoalDepositFrequency = 20;
    public static final int CoalDepositSize = 17;
    public static final int CoalDepositMinAltitude = 0;
    public static final int CoalDepositMaxAltitude = 127;

    public static final int IronDepositRarity = 100;
    public static final int IronDepositFrequency = 20;
    public static final int IronDepositSize = 9;
    public static final int IronDepositMinAltitude = 0;
    public static final int IronDepositMaxAltitude = 63;

    public static final int GoldDepositRarity = 100;
    public static final int GoldDepositFrequency = 2;
    public static final int GoldDepositSize = 9;
    public static final int GoldDepositMinAltitude = 0;
    public static final int GoldDepositMaxAltitude = 31;

    public static final int RedstoneDepositRarity = 100;
    public static final int RedstoneDepositFrequency = 8;
    public static final int RedstoneDepositSize = 8;
    public static final int RedstoneDepositMinAltitude = 0;
    public static final int RedstoneDepositMaxAltitude = 15;

    public static final int DiamondDepositRarity = 100;
    public static final int DiamondDepositFrequency = 1;
    public static final int DiamondDepositSize = 8;
    public static final int DiamondDepositMinAltitude = 0;
    public static final int DiamondDepositMaxAltitude = 15;

    public static final int LapislazuliDepositRarity = 100;
    public static final int LapislazuliDepositFrequency = 1;
    public static final int LapislazuliDepositSize = 7;
    public static final int LapislazuliDepositMinAltitude = 0;
    public static final int LapislazuliDepositMaxAltitude = 15;

    public static final int EmeraldDepositRarity = 100;
    public static final int EmeraldDepositFrequency = 1;
    public static final int EmeraldDepositSize = 5;
    public static final int EmeraldDepositMinAltitude = 4;
    public static final int EmeraldDepositMaxAltitude = 31;

    public static final int WaterClayDepositRarity = 100;
    public static final int WaterClayDepositFrequency = 1;
    public static final int WaterClayDepositSize = 4;

    public static final int WaterSandDepositRarity = 100;
    public static final int WaterSandDepositSize = 7;

    public static final int WaterGravelDepositRarity = 100;
    public static final int WaterGravelDepositSize = 6;

    public static final int RoseDepositRarity = 100;
    public static final int RoseDepositMinAltitude = 0;

    public static final int BlueOrchidDepositRarity = 100;
    public static final int BlueOrchidDepositMinAltitude = 0;

    public static final int FlowerDepositRarity = 100;
    public static final int FlowerDepositMinAltitude = 0;

    public static final int TulipDepositRarity = 25;

    public static final int RedMushroomDepositRarity = 12;
    public static final int RedMushroomDepositMinAltitude = 0;

    public static final int BrownMushroomDepositRarity = 25;
    public static final int BrownMushroomDepositMinAltitude = 0;

    public static final int LongGrassDepositRarity = 100;
    public static final int LongGrassGroupedDepositRarity = 60;

    public static final int DoubleGrassDepositRarity = 100;
    public static final int DoubleGrassGroupedDepositRarity = 15;

    public static final int DeadBushDepositRarity = 100;

    public static final int PumpkinDepositRarity = 3;
    public static final int PumpkinDepositFrequency = 1;
    public static final int PumpkinDepositMinAltitude = 0;

    public static final int ReedDepositRarity = 100;
    public static final int ReedDepositMinAltitude = 0;

    public static final int CactusDepositRarity = 100;
    public static final int CactusDepositMinAltitude = 0;

    public static final int VinesRarity = 100;
    public static final int VinesFrequency = 50;
    public static final int VinesMinAltitude = 63;

    public static final int WaterSourceDepositRarity = 100;
    public static final int WaterSourceDepositFrequency = 20;
    public static final int WaterSourceDepositMinAltitude = 8;

    public static final int LavaSourceDepositRarity = 100;
    public static final int LavaSourceDepositFrequency = 10;
    public static final int LavaSourceDepositMinAltitude = 8;

    public static final int BoulderDepositRarity = 30;
    public static final int BoulderDepositMinAltitude = 0;
    public static final int BoulderDepositMaxAltitude = 256;

    public static final int IceSpikeDepositMinHeight = 60;
    public static final int IceSpikeDepositMaxHeight = 128;
}