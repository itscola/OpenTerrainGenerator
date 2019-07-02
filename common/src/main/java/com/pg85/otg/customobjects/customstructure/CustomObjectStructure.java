package com.pg85.otg.customobjects.customstructure;

import com.pg85.otg.OTG;
import com.pg85.otg.common.LocalBiome;
import com.pg85.otg.common.LocalWorld;
import com.pg85.otg.configuration.biome.BiomeConfig;
import com.pg85.otg.configuration.standard.PluginStandardValues;
import com.pg85.otg.customobjects.CustomObject;
import com.pg85.otg.customobjects.bo3.BO3;
import com.pg85.otg.customobjects.bo3.BO3Config;
import com.pg85.otg.customobjects.bo3.BO3Settings.SpawnHeightEnum;
import com.pg85.otg.customobjects.bo3.StructurePartSpawnHeight;
import com.pg85.otg.exception.InvalidConfigException;
import com.pg85.otg.generator.resource.CustomStructureGen;
import com.pg85.otg.logging.LogMarker;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.helpers.RandomHelper;

import java.util.*;
import java.util.Map.Entry;

/**
 * Represents a collection of all {@link CustomObject}s in a structure. It is
 * calculated by finding the branches of one object, then finding the branches
 * of those branches, etc., until
 * {@link CustomObject#getMaxBranchDepth()} is reached.
 *
 */
public class CustomObjectStructure
{
	// OTG
	
    private StructurePartSpawnHeight height;
    private Map<ChunkCoordinate, Set<CustomObjectCoordinate>> objectsToSpawn;
    private int maxBranchDepth;
    private Random worldRandom;
	
    public CustomObjectStructure(CustomObjectCoordinate start)
    {
    	IsOTGPlus = false;
    	Start = start;
    }
    
    CustomObjectStructure(Random worldRandom, LocalWorld world, CustomObjectCoordinate start)
    {
    	IsOTGPlus = false;
        StructuredCustomObject object = (StructuredCustomObject)start.getObject(); // TODO: Turned CustomObject into StructuredCustomObject, check if that doesn't cause problems. Can a non-StructuredCustomObject be passed here?

        this.World = world;
        this.worldRandom = worldRandom;
        this.Start = start;
        this.height = object.getStructurePartSpawnHeight();
        this.maxBranchDepth = object.getMaxBranchDepth();
        this.Random = RandomHelper.getRandomForCoords(start.getX(), start.getY(), start.getZ(), world.getSeed());

        // Calculate all branches and add them to a list
        objectsToSpawn = new LinkedHashMap<ChunkCoordinate, Set<CustomObjectCoordinate>>();

        addToSpawnList(start, object); // Add the object itself
        addBranches(start, 1);
    }
	
	// OTG+

	private SmoothingAreaGenerator smoothingAreaManager = new SmoothingAreaGenerator();
	private EntitiesManager entitiesManager = new EntitiesManager();
	public ParticlesManager particlesManager = new ParticlesManager();
	public ModDataManager modDataManager = new ModDataManager();
	public SpawnerManager spawnerManager = new SpawnerManager();
	
    private LocalWorld World;
    private Random Random;

    // The origin BO3 for this branching structure
    public CustomObjectCoordinate Start;

    // Stores all the branches of this branching structure that should spawn along with the chunkcoordinates they should spawn in
    Map<ChunkCoordinate, Stack<CustomObjectCoordinate>> ObjectsToSpawn = new HashMap<ChunkCoordinate, Stack<CustomObjectCoordinate>>();
    public Map<ChunkCoordinate, String> ObjectsToSpawnInfo = new HashMap<ChunkCoordinate, String>();
   
    boolean IsSpawned;
    private boolean IsStructureAtSpawn = false;

    private int MinY;

    private boolean IsOTGPlus = false;

    // A smoothing area is drawn around all outer blocks (or blocks neighbouring air) on the lowest layer of blocks in each BO3 of this branching structure that has a SmoothRadius set greater than 0.
    // Object[] { int startpoint, int endpoint, int distance from real startpoint }
    Map<ChunkCoordinate, ArrayList<Object[]>> SmoothingAreasToSpawn = new HashMap<ChunkCoordinate, ArrayList<Object[]>>();

    private int branchesTried = 0;

    boolean startChunkBlockChecksDone = false;
    
    private Stack<BranchDataItem> AllBranchesBranchData = new Stack<BranchDataItem>();
    private HashMap<ChunkCoordinate, ArrayList<BranchDataItem>> AllBranchesBranchDataByChunk = new HashMap<ChunkCoordinate, ArrayList<BranchDataItem>>();
	private HashMap<String, ArrayList<ChunkCoordinate>> AllBranchesBranchDataByName = new HashMap<String, ArrayList<ChunkCoordinate>>(); // Used to find distance between branches and branch groups
	private HashMap<String, HashMap<ChunkCoordinate, ArrayList<Integer>>> AllBranchesBranchDataByGroup = new HashMap<String, HashMap<ChunkCoordinate, ArrayList<Integer>>>(); // Used to find distance between branches and branch groups
    private HashSet<Integer> AllBranchesBranchDataHash = new HashSet<Integer>();
    private boolean SpawningCanOverrideBranches = false;
    private int Cycle = 0;
    
    private BranchDataItem currentSpawningRequiredChildrenForOptionalBranch;
    private boolean spawningRequiredChildrenForOptionalBranch = false;
    private boolean spawnedBranchThisCycle = false;
    private boolean spawnedBranchLastCycle = false;
    
    CustomObjectStructure(LocalWorld world, CustomObjectCoordinate structureStart, Map<ChunkCoordinate, Stack<CustomObjectCoordinate>> objectsToSpawn, Map<ChunkCoordinate, ArrayList<Object[]>> smoothingAreasToSpawn, int minY)
    {
    	this(world, structureStart, false, false);
    	ObjectsToSpawn = objectsToSpawn;
    	SmoothingAreasToSpawn = smoothingAreasToSpawn;
    	MinY = minY;
    }
    
    CustomObjectStructure(LocalWorld world, CustomObjectCoordinate start, boolean spawn, boolean isStructureAtSpawn)
    {
        World = world;
        IsStructureAtSpawn = isStructureAtSpawn;
        IsOTGPlus = true;

        if(start == null)
        {
        	return;
        }
        if (!(start.getObject() instanceof StructuredCustomObject))
        {
            throw new IllegalArgumentException("Start object must be a structure!");
        }

        Start = start;
        Random = RandomHelper.getRandomForCoords(start.getX() + 8, start.getY(), start.getZ() + 7, world.getSeed());

		if(spawn)
		{
			branchesTried = 0;

			long startTime = System.currentTimeMillis();

			// Structure at spawn can't hurt to query source blocks, structures with randomY don't need to do any block checks so don't hurt either.
			//if(isStructureAtSpawn || ((BO3)Start.getObject(World.getName())).settings.spawnHeight == SpawnHeightEnum.randomY)
			{
				if(!doStartChunkBlockChecks()){ return; } // Just do the damn checks to get the height right....
			}

			// Only detect Y or material of source block if necessary to prevent chunk loading
			// if this BO3 is being plotted in a chunk that has not yet been populated.

			// Need to know the height if this structure can only spawn at a certain height
			//if((((BO3)Start.getObject()).getSettings().spawnHeight == SpawnHeightEnum.highestBlock || ((BO3)Start.getObject()).getSettings().spawnHeight == SpawnHeightEnum.highestSolidBlock) && (World.getConfigs().getWorldConfig().disableBedrock || ((BO3)Start.getObject()).getSettings().minHeight > 1 || ((BO3)Start.getObject()).getSettings().maxHeight < 256))
			{
				//if(!DoStartChunkBlockChecks()){ return; }
			}

			if(!((BO3)Start.getObject()).getSettings().CanSpawnOnWater)
			{
				//if(!DoStartChunkBlockChecks()){ return; }
				int highestBlocky = world.getHighestBlockYAt(Start.getX() + 8, Start.getZ() + 7, true, true, false, true);;
				//if(Start.y - 1 > OTG.WORLD_DEPTH && Start.y - 1 < OTG.WORLD_HEIGHT && world.getMaterial(Start.getX() + 8, Start.y - 1, Start.getZ() + 7).isLiquid())
				if(Start.y - 1 > PluginStandardValues.WORLD_DEPTH && Start.y - 1 < PluginStandardValues.WORLD_HEIGHT && world.getMaterial(Start.getX() + 8, highestBlocky, Start.getZ() + 7, IsOTGPlus).isLiquid())
				{
					return;
				}
			}

			if(((BO3)Start.getObject()).getSettings().SpawnOnWaterOnly)
			{
				//if(!DoStartChunkBlockChecks()){ return; }
				if(
					!(
						world.getMaterial(Start.getX(), Start.y - 1, Start.getZ(), IsOTGPlus).isLiquid() &&
						world.getMaterial(Start.getX(), Start.y - 1, Start.getZ() + 15, IsOTGPlus).isLiquid() &&
						world.getMaterial(Start.getX() + 15, Start.y - 1, Start.getZ(), IsOTGPlus).isLiquid() &&
						world.getMaterial(Start.getX() + 15, Start.y - 1, Start.getZ() + 15, IsOTGPlus).isLiquid()
					)
				)
				{
					return;
				}
			}

			try
			{
				calculateBranches(false);
			} catch (InvalidConfigException ex) {
				OTG.log(LogMarker.FATAL, "An unknown error occurred while calculating branches for BO3 " + Start.BO3Name + ". This is probably an error in the BO3's branch configuration, not a bug. If you can track this down, please tell me what caused it!");
				throw new RuntimeException();
			}

			for(Entry<ChunkCoordinate, Stack<CustomObjectCoordinate>> chunkCoordSet : ObjectsToSpawn.entrySet())
			{
				String structureInfo = "";
				for(CustomObjectCoordinate customObjectCoord : chunkCoordSet.getValue())
				{
					structureInfo += customObjectCoord.getObject().getName() + ":" + customObjectCoord.getRotation() + ", ";
				}
				if(structureInfo.length() > 0)
				{
					structureInfo = structureInfo.substring(0,  structureInfo.length() - 2);
					ObjectsToSpawnInfo.put(chunkCoordSet.getKey(), "Branches in chunk X" + chunkCoordSet.getKey().getChunkX() + " Z" + chunkCoordSet.getKey().getChunkZ() + " : " + structureInfo);
				}
			}

			for(Entry<ChunkCoordinate, Stack<CustomObjectCoordinate>> chunkCoordSet : ObjectsToSpawn.entrySet())
			{
	        	// Don't spawn BO3's that have been overriden because of replacesBO3
	        	for (CustomObjectCoordinate coordObject : chunkCoordSet.getValue())
	        	{
	        		BO3Config objectConfig = ((BO3)coordObject.getObject()).getSettings();
	        		if(objectConfig.replacesBO3Branches.size() > 0)
	        		{
	        			for(String BO3ToReplace : objectConfig.replacesBO3Branches)
	        			{
	        				for (CustomObjectCoordinate coordObjectToReplace : chunkCoordSet.getValue())
	        				{
	        					if(((BO3)coordObjectToReplace.getObject()).getName().equals(BO3ToReplace))
	        					{
	        						if(checkCollision(coordObject, coordObjectToReplace))
	        						{
	        							coordObjectToReplace.isSpawned = true;
	        						}
	        					}
	        				}
	        			}
	        		}
	        	}
			}

			//TODO: Smoothing areas should count as must spawn/required branches! <-- Is this really a problem? Smoothing areas from different structures don't overlap?

	        // Calculate smoothing areas around the entire branching structure
	        // Smooth the terrain in all directions bordering the structure so
	        // that there is a smooth transition in height from the surrounding
	        // terrain to the BO3. This way BO3's won't float above the ground
	        // or spawn inside a hole with vertical walls.
			SmoothingAreasToSpawn = smoothingAreaManager.calculateSmoothingAreas(ObjectsToSpawn, Start, World);
			smoothingAreaManager.CustomObjectStructureSpawn(SmoothingAreasToSpawn);			
			
			for(ChunkCoordinate chunkCoord : ObjectsToSpawn.keySet())
			{
				World.getStructureCache().structureCache.put(chunkCoord, this);
				World.getStructureCache().getPlotter().addToStructuresPerChunkCache(chunkCoord, new ArrayList<String>());
				// Make sure not to override any ModData/Spawner/Particle data added by CustomObjects
				if(World.getStructureCache().worldInfoChunks.containsKey(chunkCoord))
				{
					CustomObjectStructure existingObject = World.getStructureCache().worldInfoChunks.get(chunkCoord);
					this.modDataManager.modData.addAll(existingObject.modDataManager.modData);
					this.particlesManager.particleData.addAll(existingObject.particlesManager.particleData);
					this.spawnerManager.spawnerData.addAll(existingObject.spawnerManager.spawnerData);
				}
				World.getStructureCache().worldInfoChunks.put(chunkCoord, this);
			}

			for(ChunkCoordinate chunkCoord : SmoothingAreasToSpawn.keySet())
			{
				World.getStructureCache().structureCache.put(chunkCoord, this);
				World.getStructureCache().getPlotter().addToStructuresPerChunkCache(chunkCoord, new ArrayList<String>());
				// Make sure not to override any ModData/Spawner/Particle data added by CustomObjects
				if(World.getStructureCache().worldInfoChunks.containsKey(chunkCoord))
				{
					CustomObjectStructure existingObject = World.getStructureCache().worldInfoChunks.get(chunkCoord);
					this.modDataManager.modData.addAll(existingObject.modDataManager.modData);
					this.particlesManager.particleData.addAll(existingObject.particlesManager.particleData);
					this.spawnerManager.spawnerData.addAll(existingObject.spawnerManager.spawnerData);
				}
				World.getStructureCache().worldInfoChunks.put(chunkCoord, this);
			}

			if(ObjectsToSpawn.size() > 0)
			{
				IsSpawned = true;
				if(OTG.getPluginConfig().spawnLog)
				{
					int totalBO3sSpawned = 0;
					for(ChunkCoordinate entry : ObjectsToSpawn.keySet())
					{
						totalBO3sSpawned += ObjectsToSpawn.get(entry).size();
					}

					OTG.log(LogMarker.INFO, Start.getObject().getName() + " " + totalBO3sSpawned + " object(s) plotted in " + (System.currentTimeMillis() - startTime) + " Ms and " + Cycle + " cycle(s), " + (branchesTried + 1) + " object(s) tried.");
				}
			}
		}
    }

    private boolean doStartChunkBlockChecks()
    {
    	if(!startChunkBlockChecksDone)
    	{
	    	startChunkBlockChecksDone = true;

	    	//OTG.log(LogMarker.INFO, "DoStartChunkBlockChecks");

			// Requesting the Y position or material of a block in an unpopulated chunk causes some of that chunk's blocks to be calculated, this is expensive and should be kept at a minimum.

			// Y checks:
			// If BO3's have a minimum and maximum Y configured by the player then we don't really need
	    	// to check if the BO3 fits in the Y direction, that is the player's responsibility!

			// Material checks:
			// A BO3 may need to perform material checks to when using !CanSpawnOnWater or SpawnOnWaterOnly

	    	int startY = 0;

			if(((BO3)Start.getObject()).getSettings().spawnHeight == SpawnHeightEnum.highestBlock || ((BO3)Start.getObject()).getSettings().spawnHeight == SpawnHeightEnum.highestSolidBlock)
			{
				if(((BO3)Start.getObject()).getSettings().SpawnAtWaterLevel)
				{
					LocalBiome biome = World.getBiome(Start.getX() + 8, Start.getZ() + 7);
					startY = biome.getBiomeConfig().useWorldWaterLevel ? World.getConfigs().getWorldConfig().waterLevelMax : biome.getBiomeConfig().waterLevelMax;
				} else {
					// OTG.log(LogMarker.INFO, "Request height for chunk X" + ChunkCoordinate.fromBlockCoords(Start.getX(), Start.getZ()).getChunkX() + " Z" + ChunkCoordinate.fromBlockCoords(Start.getX(), Start.getZ()).getChunkZ());
					// If this chunk has not yet been populated then this will cause it to be! (ObjectSpawner.Populate() is called)

					int highestBlock = 0;

					if(!((BO3)Start.getObject()).getSettings().SpawnUnderWater)
					{
						highestBlock = World.getHighestBlockYAt(Start.getX() + 8, Start.getZ() + 7, true, true, false, true);
					} else {
						highestBlock = World.getHighestBlockYAt(Start.getX() + 8, Start.getZ() + 7, true, false, true, true);
					}

					if(highestBlock < 1)
					{
						//OTG.log(LogMarker.INFO, "Structure " + Start.BO3Name + " could not be plotted at Y < 1. If you are creating empty chunks intentionally (for a sky world for instance) then make sure you don't use the highestBlock setting for your BO3's");
						if(((BO3)Start.getObject()).getSettings().heightOffset > 0) // Allow floating structures that use highestblock + heightoffset
						{
							highestBlock = ((BO3)Start.getObject()).getSettings().heightOffset;
						} else {
							return false;
						}
					} else {
						startY  = highestBlock + 1;
					}
				}
			} else {
				if(((BO3)Start.getObject()).getSettings().maxHeight != ((BO3)Start.getObject()).getSettings().minHeight)
				{
					startY = ((BO3)Start.getObject()).getSettings().minHeight + new Random().nextInt(((BO3)Start.getObject()).getSettings().maxHeight - ((BO3)Start.getObject()).getSettings().minHeight);
				} else {
					startY = ((BO3)Start.getObject()).getSettings().minHeight;
				}
			}

			//if((MinY + startY) < 1 || (startY) < ((BO3)Start.getObject(World.getName())).settings.minHeight || (startY) > ((BO3)Start.getObject(World.getName())).settings.maxHeight)
			if(startY < ((BO3)Start.getObject()).getSettings().minHeight || startY > ((BO3)Start.getObject()).getSettings().maxHeight)
			{
				return false;
				//throw new IllegalArgumentException("Structure could not be plotted at these coordinates, it does not fit in the Y direction. " + ((BO3)Start.getObject(World.getName())).getName() + " at Y " + startY);
			}

			startY += ((BO3)Start.getObject()).getSettings().heightOffset;

			if(startY < PluginStandardValues.WORLD_DEPTH || startY >= PluginStandardValues.WORLD_HEIGHT)
			{
				return false;
			}

			for(ChunkCoordinate chunkCoord : ObjectsToSpawn.keySet())
			{
				for(CustomObjectCoordinate BO3 : ObjectsToSpawn.get(chunkCoord))
				{
					BO3.y += startY;
				}
			}

			Map<ChunkCoordinate, ArrayList<Object[]>> SmoothingAreasToSpawn2 = new HashMap<ChunkCoordinate, ArrayList<Object[]>>();
			SmoothingAreasToSpawn2.putAll(SmoothingAreasToSpawn);
			SmoothingAreasToSpawn.clear();
			for(ChunkCoordinate chunkCoord2 : SmoothingAreasToSpawn2.keySet())
			{
				ArrayList<Object[]> coords = new ArrayList<Object[]>();
				Object[] coordToAdd;
				for(Object[] coord : SmoothingAreasToSpawn2.get(chunkCoord2))
				{
					if(coord.length == 18)
					{
						coordToAdd = new Object[]{ ((Integer)coord[0]), ((Integer)coord[1]) + Start.getY(), ((Integer)coord[2]), ((Integer)coord[3]), ((Integer)coord[4]) + Start.getY(), ((Integer)coord[5]), ((Integer)coord[6]), -1, ((Integer)coord[8]), ((Integer)coord[9]), -1, ((Integer)coord[11]), ((Integer)coord[12]), ((Integer)coord[13]) + Start.getY(), ((Integer)coord[14]), ((Integer)coord[15]), -1, ((Integer)coord[17]) };
						coords.add(coordToAdd);
					}
					else if(coord.length == 12)
					{
						coordToAdd = new Object[]{ ((Integer)coord[0]), ((Integer)coord[1]) + Start.getY(), ((Integer)coord[2]), ((Integer)coord[3]), ((Integer)coord[4]) + Start.getY(), ((Integer)coord[5]), ((Integer)coord[6]), ((Integer)coord[7]) + Start.getY(), ((Integer)coord[8]), ((Integer)coord[9]), -1, ((Integer)coord[11]) };
						coords.add(coordToAdd);
					} else {
						throw new RuntimeException(); // TODO: Remove after testing.
					}
				}
				SmoothingAreasToSpawn.put(ChunkCoordinate.fromChunkCoords(chunkCoord2.getChunkX(), chunkCoord2.getChunkZ()), coords);
			}

			Start.y = startY;
    	}
    	return true;
    }
   
    /**
     * Gets an Object[] { ChunkCoordinate, ChunkCoordinate } containing the top left and bottom right chunk
     * If this structure were spawned as small as possible (with branchDepth 0)
     * @param world
     * @param start
     * @return
     * @throws InvalidConfigException
     */
    public Object[] getMinimumSize() throws InvalidConfigException
    {
    	if(
			((BO3)Start.getObject()).getSettings().MinimumSizeTop != -1 &&
			((BO3)Start.getObject()).getSettings().MinimumSizeBottom != -1 &&
			((BO3)Start.getObject()).getSettings().MinimumSizeLeft != -1 &&
			((BO3)Start.getObject()).getSettings().MinimumSizeRight != -1)
    	{
    		Object[] returnValue = { ((BO3)Start.getObject()).getSettings().MinimumSizeTop, ((BO3)Start.getObject()).getSettings().MinimumSizeRight, ((BO3)Start.getObject()).getSettings().MinimumSizeBottom, ((BO3)Start.getObject()).getSettings().MinimumSizeLeft };
    		return returnValue;
    	}
    	
    	calculateBranches(true);

        // Calculate smoothing areas around the entire branching structure
        // Smooth the terrain in all directions bordering the structure so
        // that there is a smooth transition in height from the surrounding
        // terrain to the BO3. This way BO3's won't float above the ground
        // or spawn inside a hole with vertical walls.

		// Don't calculate smoothing areas for minimumSize, instead just add smoothradius / 16 to each side

		ChunkCoordinate startChunk = ChunkCoordinate.fromBlockCoords(Start.getX(), Start.getZ());

		ChunkCoordinate top = startChunk;
		ChunkCoordinate left = startChunk;
		ChunkCoordinate bottom = startChunk;
		ChunkCoordinate right = startChunk;

		for(ChunkCoordinate chunkCoord : ObjectsToSpawn.keySet())
		{
			if(chunkCoord.getChunkX() > right.getChunkX())
			{
				right = chunkCoord;
			}
			if(chunkCoord.getChunkZ() > bottom.getChunkZ())
			{
				bottom = chunkCoord;
			}
			if(chunkCoord.getChunkX() < left.getChunkX())
			{
				left = chunkCoord;
			}
			if(chunkCoord.getChunkZ() < top.getChunkZ())
			{
				top = chunkCoord;
			}
			for(CustomObjectCoordinate struct : ObjectsToSpawn.get(chunkCoord))
			{
				if(struct.getY() < MinY)
				{
					MinY = struct.getY();
				}
			}
		}

		MinY += ((BO3)Start.getObject()).getSettings().heightOffset;

		int smoothingRadiusInChunks = (int)Math.ceil(((BO3)Start.getObject()).getSettings().smoothRadius / (double)16);  // TODO: this assumes that smoothradius is the same for every BO3 within this structure, child branches may have overriden their own smoothradius! This may cause problems if a child branch has a larger smoothradius than the starting structure
    	((BO3)Start.getObject()).getSettings().MinimumSizeTop = Math.abs(startChunk.getChunkZ() - top.getChunkZ()) + smoothingRadiusInChunks;
    	((BO3)Start.getObject()).getSettings().MinimumSizeRight = Math.abs(startChunk.getChunkX() - right.getChunkX()) + smoothingRadiusInChunks;
    	((BO3)Start.getObject()).getSettings().MinimumSizeBottom = Math.abs(startChunk.getChunkZ() - bottom.getChunkZ()) + smoothingRadiusInChunks;
    	((BO3)Start.getObject()).getSettings().MinimumSizeLeft = Math.abs(startChunk.getChunkX() - left.getChunkX()) + smoothingRadiusInChunks;

    	Object[] returnValue = { ((BO3)Start.getObject()).getSettings().MinimumSizeTop, ((BO3)Start.getObject()).getSettings().MinimumSizeRight, ((BO3)Start.getObject()).getSettings().MinimumSizeBottom, ((BO3)Start.getObject()).getSettings().MinimumSizeLeft };

    	if(OTG.getPluginConfig().spawnLog)
    	{
    		OTG.log(LogMarker.INFO, "");
        	OTG.log(LogMarker.INFO, Start.getObject().getName() + " minimum size: Width " + ((Integer)returnValue[1] + (Integer)returnValue[3] + 1) + " Length " + ((Integer)returnValue[0] + (Integer)returnValue[2] + 1) + " top " + (Integer)returnValue[0] + " right " + (Integer)returnValue[1] + " bottom " + (Integer)returnValue[2] + " left " + (Integer)returnValue[3]);
    	}

    	ObjectsToSpawn.clear();

    	return returnValue;
    }

    // TODO: Make sure that canOverride optional branches cannot be in the same branch group as required branches.
    // This makes sure that when the first spawn phase is complete and all required branches and non-canOverride optional branches have spawned
    // those can never be rolled back because of canOverride optional branches that are unable to spawn.
    // canOverride required branches: things that need to be spawned in the same cycle as their parent branches, for instance door/wall markers for rooms
    // canOverride optional branches: things that should be spawned after the base of the structure has spawned, for instance room interiors, adapter/modifier pieces that knock out walls/floors between rooms etc.

    private void calculateBranches(boolean minimumSize) throws InvalidConfigException
    {
    	if(OTG.getPluginConfig().spawnLog)
    	{
	    	String sminimumSize = minimumSize ? " (minimumSize)" : "";
	    	OTG.log(LogMarker.INFO, "");
	    	OTG.log(LogMarker.INFO, "-------- CalculateBranches " + Start.BO3Name + sminimumSize +" --------");
    	}

        BranchDataItem branchData = new BranchDataItem(World, Random, null, Start, null, 0, 0, minimumSize);

        if(OTG.getPluginConfig().spawnLog)
        {
        	OTG.log(LogMarker.INFO, "");
	        OTG.log(LogMarker.INFO, "---- Cycle 0 ----");
	        OTG.log(LogMarker.INFO, "Plotted X" + branchData.ChunkCoordinate.getChunkX() + " Z" + branchData.ChunkCoordinate.getChunkZ() + " - " + branchData.Branch.getObject().getName());
        }

        addToCaches(branchData, ((BO3)branchData.Branch.getObject()));       

    	Cycle = 0;
    	boolean canOverrideBranchesSpawned = false;
    	SpawningCanOverrideBranches = false;
    	boolean processingDone = false;
    	while(!processingDone)
    	{
    		spawnedBranchLastCycle = spawnedBranchThisCycle;
    		spawnedBranchThisCycle = false;

    		Cycle += 1;

    		if(OTG.getPluginConfig().spawnLog)
    		{
    			OTG.log(LogMarker.INFO, "");
    			OTG.log(LogMarker.INFO, "---- Cycle " + Cycle + " ----");
    		}

    		traverseAndSpawnChildBranches(branchData, minimumSize, true);

			if(OTG.getPluginConfig().spawnLog)
			{
				OTG.log(LogMarker.INFO, "All branch groups with required branches only have been processed for cycle " + Cycle + ", plotting branch groups with optional branches.");
			}
			traverseAndSpawnChildBranches(branchData, minimumSize, false);

			processingDone = true;
            for(BranchDataItem branchDataItem3 : AllBranchesBranchData)
            {
            	if(!branchDataItem3.DoneSpawning)
            	{
            		processingDone = false;
            		break;
            	}
            }

        	// CanOverride optional branches are spawned only after the main structure has spawned.
        	// This is useful for knocking out walls between rooms and adding interiors.
            if(processingDone && !canOverrideBranchesSpawned)
            {
            	canOverrideBranchesSpawned = true;
            	SpawningCanOverrideBranches = true;
            	processingDone = false;
	            for(BranchDataItem branchDataItem3 : AllBranchesBranchData)
	            {
	            	for(BranchDataItem childBranch : branchDataItem3.getChildren(false))
	            	{
	            		if(
            				!childBranch.Branch.isRequiredBranch &&
            				((BO3)childBranch.Branch.getObject()).getSettings().canOverride
        				)
	            		{
	            			branchDataItem3.DoneSpawning = false;
	            			childBranch.DoneSpawning = false;
	            			childBranch.CannotSpawn = false;

	            			if(branchDataItem3.wasDeleted)
	            			{
	            				throw new RuntimeException(); // TODO: Remove after testing
	            			}

	            			if(childBranch.wasDeleted)
	            			{
	            				throw new RuntimeException(); // TODO: Remove after testing
	            			}
	            		}
	            	}
	            }
            }

    		if(branchData.CannotSpawn)
    		{
    			if(minimumSize)
    			{
    				if(OTG.getPluginConfig().spawnLog)
    				{
    					OTG.log(LogMarker.WARN, "Error: Branching BO3 " + Start.BO3Name + " could not be spawned in minimum configuration (isRequiredBranch branches only).");
    				}
            		throw new InvalidConfigException("Error: Branching BO3 " + Start.BO3Name + " could not be spawned in minimum configuration (isRequiredBranch branches only).");
    			}
    			
    	        AllBranchesBranchData.clear();
    	        AllBranchesBranchDataByChunk.clear();
    	    	AllBranchesBranchDataByName.clear();
    	    	AllBranchesBranchDataByGroup.clear();
    	        AllBranchesBranchDataHash.clear();
    			
    			return;
    		}
    	}

        for(BranchDataItem branchToAdd : AllBranchesBranchData)
        {
        	if(!branchToAdd.CannotSpawn)
        	{
        		if(branchToAdd.Branch == null)
        		{
        			throw new RuntimeException(); // TODO: Remove after testing
        		}
        		addToChunk(branchToAdd.Branch, branchToAdd.ChunkCoordinate, ObjectsToSpawn);
        	}
        }
        
        AllBranchesBranchData.clear();
        AllBranchesBranchDataByChunk.clear();
    	AllBranchesBranchDataByName.clear();
    	AllBranchesBranchDataByGroup.clear();
        AllBranchesBranchDataHash.clear();
    }

    private void traverseAndSpawnChildBranches(BranchDataItem branchData, boolean minimumSize, boolean spawningRequiredBranchesOnly)
    {
    	if(!branchData.DoneSpawning)
    	{
    		addBranches(branchData, minimumSize, false, spawningRequiredBranchesOnly);
    	} else {
    		if(!branchData.CannotSpawn)
    		{
    			for(BranchDataItem branchDataItem2 : branchData.getChildren(false))
    			{
    				// BranchData.DoneSpawning can be set to true by a child branch
    				// that tried to spawn but couldnt
    				if(!branchDataItem2.CannotSpawn && branchData.DoneSpawning)
    				{
    					traverseAndSpawnChildBranches(branchDataItem2, minimumSize, spawningRequiredBranchesOnly);
    				}
    			}
    		}
    	}
    }

    private void addBranches(BranchDataItem branchDataItem, boolean minimumSize, boolean traverseOnlySpawnedChildren, boolean spawningRequiredBranchesOnly)
    {
    	// CanOverride optional branches are spawned only after the main structure has spawned.
    	// This is useful for adding interiors and knocking out walls between rooms
    	if(!SpawningCanOverrideBranches)
    	{
	    	for(BranchDataItem branchDataItem3 : branchDataItem.getChildren(false))
	    	{
	    		if(
    				(
						!branchDataItem3.CannotSpawn ||
						!branchDataItem3.DoneSpawning
					) && (
						((BO3)branchDataItem3.Branch.getObject()).getSettings().canOverride &&
						!branchDataItem3.Branch.isRequiredBranch
					)
				)
	    		{
	    			branchDataItem3.CannotSpawn = true;
	    			branchDataItem3.DoneSpawning = true;
	    		}
	    	}
    	}

    	// TODO: Remove these
    	if(spawningRequiredChildrenForOptionalBranch && traverseOnlySpawnedChildren)
    	{
    		throw new RuntimeException();
    	}

    	// If we are spawning optional branches then we know this branch will be done spawning when this method returns
    	// (all optional branches will try to spawn, then if none have spawned any leftover required branches will try to spawn)
    	// and won't try to spawn anything in the second phase of this branch spawning cycle
    	if(!spawningRequiredBranchesOnly)// || isRollBack)
    	{
    		branchDataItem.DoneSpawning = true;
    	} else {
    		// If we are spawning required branches then there might also
    		// be optional branches, which will not have had a chance to spawn when this method returns
    		// The second (optional branches) phase of this branch spawning cycle will call AddBranches on the branch for the
    		// second time to try to spawn them and will set DoneSpawning to true.
			boolean hasOnlyRequiredBranches = true;
			for(BranchDataItem branchDataItem3 : branchDataItem.getChildren(false))
			{
				if(!branchDataItem3.Branch.isRequiredBranch && !branchDataItem3.DoneSpawning && !branchDataItem3.CannotSpawn)
				{
					hasOnlyRequiredBranches = false;
					break;
				}
			}
			if(hasOnlyRequiredBranches)
			{
				// if this branch has only required branches then we know
				// it won't be spawning anything in the second phase of
				// this branch spawning cycle
				branchDataItem.DoneSpawning = true;
			}
    	}

    	if(!branchDataItem.CannotSpawn)
    	{    		
	        for(BranchDataItem childBranchDataItem : branchDataItem.getChildren(false))
	        {        	
	        	if(!AllBranchesBranchDataHash.contains(childBranchDataItem.branchNumber) && !childBranchDataItem.SpawnDelayed)
	        	{
		        	// Check if children should be spawned
		        	// Check 1: Check for collision with other branches or other structures
	        		boolean canSpawn = true;

	        		boolean collidedWithParentOrSibling = false;
	        		boolean wasntBelowOther = false;
	        		boolean wasntInsideOther = false;
	        		boolean cannotSpawnInsideOther = false;
	        		boolean wasntOnWater = false;
	        		boolean wasOnWater = false;
	        		boolean spaceIsOccupied = false;
	        		boolean chunkIsIneligible = false;
	        		boolean startChunkBlockChecksPassed = true;
	        		boolean isInsideWorldBorder = true;
        			boolean branchFrequencyNotPassed = false;
        			boolean branchFrequencyGroupsNotPassed = false;

        			BO3 bo3 = ((BO3)childBranchDataItem.Branch.getObject());

        			if(bo3 == null || bo3.isInvalidConfig)
        			{
		        		childBranchDataItem.DoneSpawning = true;
		        		childBranchDataItem.CannotSpawn = true;
		        		if(bo3 == null)
		        		{
		        			if(OTG.getPluginConfig().spawnLog)
		        			{
		        				OTG.log(LogMarker.WARN, "Error: Could not find BO3 file: " + childBranchDataItem.Branch.BO3Name + ".BO3 which is a branch of " + branchDataItem.Branch.BO3Name + ".BO3");
		        			}
		        		}
        			}

	        		if(childBranchDataItem.DoneSpawning || childBranchDataItem.CannotSpawn)
	        		{
	        			continue;
	        		}

	        		// Before spawning any required branch make sure there are no optional branches in its branch group that haven't tried to spawn yet.
    	        	if(spawningRequiredBranchesOnly)// && !isRollBack)
    	        	{
    	    			if(childBranchDataItem.Branch.isRequiredBranch)
    	    			{
    		    			boolean hasOnlyRequiredBranches = true;
    		    			if(childBranchDataItem.Branch.branchGroup != null && childBranchDataItem.Branch.branchGroup.length() > 0)
    		    			{
	    		    			for(BranchDataItem branchDataItem3 : branchDataItem.getChildren(false))
	    		    			{
	    		    				if(
    		    						!branchDataItem3.Branch.isRequiredBranch &&
    		    						branchDataItem3.Branch.branchGroup != null &&
    		    						branchDataItem3.Branch.branchGroup.length() > 0 &&
    		    						childBranchDataItem.Branch.branchGroup.equals(branchDataItem3.Branch.branchGroup) &&
    		    						!branchDataItem3.wasDeleted &&
	    								!branchDataItem3.CannotSpawn &&
		    							!branchDataItem3.DoneSpawning
		    						)
	    		    				{
	    		    					hasOnlyRequiredBranches = false;
	    		    					break;
	    		    				}
	    		    			}
    		    			}
    		    			if(!hasOnlyRequiredBranches)
    		    			{
    		    				continue;
    		    			}
    	    			} else {
    	    				continue;
    	    			}
    	        	}

	        		if(canSpawn && (childBranchDataItem.MaxDepth == 0 || childBranchDataItem.CurrentDepth > childBranchDataItem.MaxDepth) && !childBranchDataItem.Branch.isRequiredBranch)
	        		{
	        			canSpawn = false;
	        		}

	        		branchesTried += 1;

	        		// Ignore weightedbranches when measuring
	        		if(minimumSize && childBranchDataItem.Branch.isWeightedBranch)
	        		{
	        			childBranchDataItem.DoneSpawning = true;
        				childBranchDataItem.CannotSpawn = true;
        				continue;
	        		}

	        		int smoothRadius = ((BO3)Start.getObject()).getSettings().overrideChildSettings && bo3.getSettings().overrideChildSettings ? ((BO3)Start.getObject()).getSettings().smoothRadius : bo3.getSettings().smoothRadius;
	        		if(smoothRadius == -1 || bo3.getSettings().smoothRadius == -1)
	        		{
	        			smoothRadius = 0;
	        		}

	        		ChunkCoordinate worldBorderCenterPoint = World.getWorldSession().getWorldBorderCenterPoint();

	        		if(
        				canSpawn &&
        				!minimumSize &&
        				World.getWorldSession().getWorldBorderRadius() > 0 &&
        				(
    						(
								smoothRadius == 0 &&
								!World.isInsideWorldBorder(ChunkCoordinate.fromChunkCoords(childBranchDataItem.Branch.getChunkX(), childBranchDataItem.Branch.getChunkZ()), true)
							)
    						||
    						(
								smoothRadius > 0 &&
								(
									childBranchDataItem.Branch.getChunkX() - Math.ceil(smoothRadius / (double)16) < worldBorderCenterPoint.getChunkX() - (World.getWorldSession().getWorldBorderRadius() - 1) ||
									childBranchDataItem.Branch.getChunkX() + Math.ceil(smoothRadius / (double)16) > worldBorderCenterPoint.getChunkX() + (World.getWorldSession().getWorldBorderRadius() - 1) - 1 || // Resources are spawned at an offset of + half a chunk so stop 1 chunk short of the border
									childBranchDataItem.Branch.getChunkZ() - Math.ceil(smoothRadius / (double)16) < worldBorderCenterPoint.getChunkZ() - (World.getWorldSession().getWorldBorderRadius() - 1) ||
									childBranchDataItem.Branch.getChunkZ() + Math.ceil(smoothRadius / (double)16) > worldBorderCenterPoint.getChunkZ() + (World.getWorldSession().getWorldBorderRadius() - 1) - 1 // Resources are spawned at an offset of + half a chunk so stop 1 chunk short of the border
								)
							)
						)
    				)
	        		{
	        			canSpawn = false;
	        			isInsideWorldBorder = false;
	        		}

        			if(!doStartChunkBlockChecks())
        			{
        				canSpawn = false;
        				startChunkBlockChecksPassed = false;
        			} else {
		        	    if(childBranchDataItem.Branch.getY() < 0 && !minimumSize)
		        	    {
		    		    	canSpawn = false;
		        	    }
        			}

	        		Stack<BranchDataItem> collidingObjects = null;
	        		if(canSpawn)
	        		{
	        			if(!minimumSize && World.chunkHasDefaultStructure(this.worldRandom, childBranchDataItem.ChunkCoordinate))
	        			{
	        				chunkIsIneligible = true;
	        				canSpawn = false;
	        			}
	        			
		        		if(canSpawn && !minimumSize && bo3.getSettings().SpawnOnWaterOnly)
		    			{
		    				if(
		    					!(
		    						World.getMaterial(childBranchDataItem.ChunkCoordinate.getBlockX(), World.getHighestBlockYAt(childBranchDataItem.ChunkCoordinate.getBlockX(), childBranchDataItem.ChunkCoordinate.getBlockZ(), true, true, false, true), childBranchDataItem.ChunkCoordinate.getBlockZ(), IsOTGPlus).isLiquid() &&
		    						World.getMaterial(childBranchDataItem.ChunkCoordinate.getBlockX(), World.getHighestBlockYAt(childBranchDataItem.ChunkCoordinate.getBlockX(), childBranchDataItem.ChunkCoordinate.getBlockZ() + 15, true, true, false, true), childBranchDataItem.ChunkCoordinate.getBlockZ() + 15, IsOTGPlus).isLiquid() &&
		    						World.getMaterial(childBranchDataItem.ChunkCoordinate.getBlockX() + 15, World.getHighestBlockYAt(childBranchDataItem.ChunkCoordinate.getBlockX() + 15, childBranchDataItem.ChunkCoordinate.getBlockZ(), true, true, false, true), childBranchDataItem.ChunkCoordinate.getBlockZ(), IsOTGPlus).isLiquid() &&
		    						World.getMaterial(childBranchDataItem.ChunkCoordinate.getBlockX() + 15, World.getHighestBlockYAt(childBranchDataItem.ChunkCoordinate.getBlockX() + 15, childBranchDataItem.ChunkCoordinate.getBlockZ() + 15, true, true, false, true), childBranchDataItem.ChunkCoordinate.getBlockZ() + 15, IsOTGPlus).isLiquid()
		    					)
		    				)
		    				{
		    					wasntOnWater = true;
		    					canSpawn = false;
		    				}
		    			}
		        		if(canSpawn && !minimumSize && !bo3.getSettings().CanSpawnOnWater)
		    			{
		    				if(
	    						(World.getMaterial(childBranchDataItem.ChunkCoordinate.getBlockX() + 8, World.getHighestBlockYAt(childBranchDataItem.ChunkCoordinate.getBlockX() + 8, childBranchDataItem.ChunkCoordinate.getBlockZ() + 7, true, true, false, true), childBranchDataItem.ChunkCoordinate.getBlockZ() + 7, IsOTGPlus).isLiquid())
		    				)
		    				{
		    					wasOnWater = true;
		    					canSpawn = false;
		    				}
		    			}

	        			if(canSpawn && bo3.getSettings().mustBeBelowOther)
	        			{
	        				canSpawn = checkMustBeBelowOther(childBranchDataItem);
	        				if(!canSpawn)
	        				{
	        					wasntBelowOther = true;
	        				}
	        			}

	        			if(canSpawn && bo3.getSettings().mustBeInsideBranches.size() > 0)
	        			{	        				
	        				canSpawn = checkMustBeInside(childBranchDataItem, bo3);
	        				if(!canSpawn)
	        				{
	        					wasntInsideOther = true;
	        				}
	        			}

	        			if(canSpawn && bo3.getSettings().cannotBeInsideBranches.size() > 0)
	        			{
	        				canSpawn = checkCannotBeInside(childBranchDataItem, bo3);
	        				if(!canSpawn)
	        				{
	        					cannotSpawnInsideOther = true;
	        				}
	        			}

	        		    if(canSpawn && bo3.getSettings().branchFrequency > 0)
	        		    {
	        		    	canSpawn = checkBranchFrequency(childBranchDataItem, bo3);
	        		    	if(!canSpawn)
	        		    	{
	        		    		branchFrequencyNotPassed = true;
	        		    	}
	        		    }
	        		    
	        		    if(canSpawn && bo3.getSettings().branchFrequencyGroups.size() > 0)
	        		    {
	        		    	canSpawn = checkBranchFrequencyGroups(childBranchDataItem, bo3);
	        		    	if(!canSpawn)
	        		    	{
	        		    		branchFrequencyGroupsNotPassed = true;
	        		    	}
	        		    }	        		    

	        			if(canSpawn)
	        			{
	        				// Returns collidingObject == null if if the branch cannot spawn in the given biome or if the given chunk is occupied by another structure
	    					collidingObjects = checkSpawnRequirementsAndCollisions(childBranchDataItem, minimumSize);
	        				if(collidingObjects.size() > 0)
	        				{
		    					canSpawn = false;
		    					collidedWithParentOrSibling = true;

		        				for(BranchDataItem collidingObject : collidingObjects)
		        				{
		        					// TODO: siblings canOverride children are not taken into account atm!
		        					// TODO: all canOverride branches are now being ignored, change that??

		        					if(collidingObject == null)
		        					{
		        						chunkIsIneligible = true;
		        						collidedWithParentOrSibling = false;
		        						break;
		        					}

	    							//OTG.log(LogMarker.INFO, "collided with: " + collidingObject.BO3Name);

		        					if(
	        							(
        									branchDataItem.Parent == null ||
        									collidingObject.Branch != branchDataItem.Parent.Branch
    									) &&
    									!((BO3) collidingObject.Branch.getObject()).getSettings().canOverride
									)
		        					{
		        						boolean siblingFound = false;
		        						if(branchDataItem.Parent != null)
		        						{
			        						for(BranchDataItem parentSibling : branchDataItem.Parent.getChildren(false))
			        						{
			        							if(collidingObject.Branch == parentSibling.Branch)
			        							{
				        							siblingFound = true;
				        							break;
			        							}
			        						}
		        						}
		        						if(!siblingFound)
		        						{
			        						for(BranchDataItem sibling : branchDataItem.getChildren(false))
			        						{
			        							if(collidingObject.Branch == sibling.Branch)
			        							{
				        							siblingFound = true;
				        							break;
			        							}
			        						}
		        						}
		        						if(!siblingFound)
		        						{
		        							spaceIsOccupied = true;
		        							collidedWithParentOrSibling = false;
		        							break;
		        						}
		        					}
		        				}
	        				}
	        			}
	        		}

		        	if(canSpawn)
		        	{
		        		if(OTG.getPluginConfig().spawnLog)
		        		{

			        		String allParentsString = "";
			        		BranchDataItem tempBranch = childBranchDataItem;
			        		while(tempBranch.Parent != null)
			        		{
			        			allParentsString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ() + " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
			        			tempBranch = tempBranch.Parent;
			        		}

			        		OTG.log(LogMarker.INFO, "Plotted X" + childBranchDataItem.ChunkCoordinate.getChunkX() + " Z" + childBranchDataItem.ChunkCoordinate.getChunkZ() + (minimumSize ? "" : " Y" + (childBranchDataItem.Branch.getY())) + " " +  childBranchDataItem.Branch.BO3Name + ":" + childBranchDataItem.Branch.getRotation() + (childBranchDataItem.Branch.isRequiredBranch ? " required" : " optional") + " cycle " + Cycle + allParentsString);
		        		}

	        	    	if(childBranchDataItem.getChildren(false).size() == 0)
	        	    	{
	        	    		childBranchDataItem.DoneSpawning = true;
	        	    	}

	        	    	// Mark any required branches in the same branch group so they wont try to spawn
		        		for(BranchDataItem childBranchDataItem2 : branchDataItem.getChildren(false))
		        		{
		        			if(
	        					childBranchDataItem2 != childBranchDataItem &&
	    						(childBranchDataItem.Branch.branchGroup != null && childBranchDataItem.Branch.branchGroup.length() >= 0) &&
	        					childBranchDataItem.Branch.branchGroup.equals(childBranchDataItem2.Branch.branchGroup) &&
    							childBranchDataItem2.Branch.isRequiredBranch
        					)
		        			{
		        				childBranchDataItem2.DoneSpawning = true;
		        				childBranchDataItem2.CannotSpawn = true;
        					}
		        		}

		        		spawnedBranchThisCycle = true;

		        		addToCaches(childBranchDataItem, bo3);		        		

		        		// If an optional branch spawns then immediately spawn its required branches as well (if any)
		        		// If this causes a rollback the rollback will stopped at this branch and we can resume spawning
		        		// the current branch's children as if it was unable to spawn.
		        		if(
	        				!spawningRequiredChildrenForOptionalBranch &&
	        				!childBranchDataItem.Branch.isRequiredBranch
        				)
		        		{
		        			if(OTG.getPluginConfig().spawnLog)
		        			{
		        				OTG.log(LogMarker.INFO, "Plotting all required child branches that are not in a branch group with optional branches.");
		        			}

		        			spawningRequiredChildrenForOptionalBranch = true;
        					currentSpawningRequiredChildrenForOptionalBranch = childBranchDataItem;
			        		traverseAndSpawnChildBranches(childBranchDataItem, minimumSize, true);
			        		spawningRequiredChildrenForOptionalBranch = false;

			        		// Make sure the branch wasn't rolled back because the required branches couldn't spawn.
			        		boolean bFound = false;
			        		ArrayList<BranchDataItem> branchDataItemStack = AllBranchesBranchDataByChunk.get(childBranchDataItem.ChunkCoordinate);
			        		if(branchDataItemStack != null)
			        		{
			        			for(BranchDataItem b : branchDataItemStack)
			        			{
			        				if(b == childBranchDataItem)
			        				{
			        					bFound = true;
			        					break;
			        				}
			        			}
			        		}
			        		canSpawn = bFound;

		        			if(OTG.getPluginConfig().spawnLog)
		        			{
		        				OTG.log(LogMarker.INFO, "Done spawning required children for optional branch X" + childBranchDataItem.ChunkCoordinate.getChunkX() + " Z" + childBranchDataItem.ChunkCoordinate.getChunkZ() + (minimumSize ? "" : " Y" + (childBranchDataItem.Branch.getY())) + " " +  childBranchDataItem.Branch.BO3Name + ":" + childBranchDataItem.Branch.getRotation());
		        			}
		        		}
		        		// If AddBranches was called during a rollback then only traverse branches for children that spawn during this call
		        		// Otherwise existing branches could have their children spawn more than once per cycle
		        		else if(
	        				traverseOnlySpawnedChildren &&
	        				!spawningRequiredChildrenForOptionalBranch &&
	        				childBranchDataItem.Branch.isRequiredBranch
        				)
		        		{
			        		traverseAndSpawnChildBranches(childBranchDataItem, minimumSize, true);
		        		}
		        	}

		        	if(!canSpawn)
		        	{
		        		if(!childBranchDataItem.DoneSpawning && !childBranchDataItem.CannotSpawn)
		        		{
		        			// WasntBelowOther branches that cannot spawn get to retry
		        			// each cycle unless no branch spawned last cycle
		        			// TODO: Won't this cause problems?
		        			if(!wasntBelowOther || !spawnedBranchLastCycle)
		        			{
				        		childBranchDataItem.DoneSpawning = true;
				        		childBranchDataItem.CannotSpawn = true;
		        			} else {
		        				branchDataItem.DoneSpawning = false;
		        				if(branchDataItem.wasDeleted)
		        				{
		        					throw new RuntimeException(); // TODO: Remove after testing
		        				}
		        			}

			        		boolean bBreak = false;

			        		boolean branchGroupFailedSpawning = false;
			        		if(childBranchDataItem.Branch.isRequiredBranch)
			        		{
			        			branchGroupFailedSpawning = true;

			        	    	// Check if there are any more required branches in this group that haven't tried to spawn yet.
				        		for(BranchDataItem childBranchDataItem2 : branchDataItem.getChildren(false))
				        		{
				        			if(
			        					childBranchDataItem2 != childBranchDataItem &&
			    						(childBranchDataItem.Branch.branchGroup != null && childBranchDataItem.Branch.branchGroup.length() >= 0) &&
			        					childBranchDataItem.Branch.branchGroup.equals(childBranchDataItem2.Branch.branchGroup) &&
		    							childBranchDataItem2.Branch.isRequiredBranch &&
				        				!childBranchDataItem2.DoneSpawning &&
				        				!childBranchDataItem2.CannotSpawn
		        					)
				        			{
				        				branchGroupFailedSpawning = false;
				        				break;
		        					}
				        		}
			        		}

			        		if(!collidedWithParentOrSibling && (!wasntBelowOther || !spawnedBranchLastCycle) && branchGroupFailedSpawning)
			        		{
			            		// Branch could not spawn
			            		// abort this branch because it has a branch group that could not be spawned

			            		if(OTG.getPluginConfig().spawnLog)
			            		{
			        	    		String allParentsString = "";
			        	    		BranchDataItem tempBranch = branchDataItem;
			        	    		while(tempBranch.Parent != null)
			        	    		{
			        	    			allParentsString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ() + " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
			        	    			tempBranch = tempBranch.Parent;
			        	    		}

			        	    		String occupiedByObjectsString = "";
			        	    		if(spaceIsOccupied)
			        	    		{
			        	    			for(BranchDataItem collidingObject : collidingObjects)
			        	    			{
			        	    				String occupiedByObjectString = collidingObject.Branch.BO3Name + ":" + collidingObject.Branch.getRotation() + " X" + collidingObject.Branch.getChunkX() + " Z" + collidingObject.Branch.getChunkZ() + " Y" + collidingObject.Branch.getY();
					        	    		tempBranch = collidingObject;
					        	    		while(tempBranch.Parent != null)
					        	    		{
					        	    			occupiedByObjectString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ() + " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
					        	    			tempBranch = tempBranch.Parent;
					        	    		}
					        	    		occupiedByObjectsString += " " + occupiedByObjectString;
			        	    			}
			        	    		}

			        	    		String reason = (branchFrequencyGroupsNotPassed ? "BranchFrequencyGroupNotPassed " : "") + (branchFrequencyNotPassed ? "BranchFrequencyNotPassed " : "") + (!isInsideWorldBorder ? "IsOutsideWorldBorder " : "") + (!startChunkBlockChecksPassed ? "StartChunkBlockChecksNotPassed " : "") + (collidedWithParentOrSibling ? "CollidedWithParentOrSibling " : "") + (wasntBelowOther ? "WasntBelowOther " : "") + (wasntInsideOther ? "WasntInsideOther " : "") + (cannotSpawnInsideOther ? "CannotSpawnInsideOther " : "") + (wasntOnWater ? "WasntOnWater " : "") + (wasOnWater ? "WasOnWater " : "") + (!branchFrequencyGroupsNotPassed && !branchFrequencyNotPassed && isInsideWorldBorder && startChunkBlockChecksPassed && !wasntBelowOther && !cannotSpawnInsideOther && !wasntOnWater && !wasOnWater && !wasntBelowOther && !chunkIsIneligible && spaceIsOccupied ? "SpaceIsOccupied by" + occupiedByObjectsString : "") + (wasntBelowOther ? "WasntBelowOther " : "") + (chunkIsIneligible ? "TerrainIsUnsuitable (StartChunkBlockChecks (height or material) not passed or Y < 0 or Frequency/BO3Group checks not passed or BO3 collided with other CustomStructure or smoothing area collided with other CustomStructure or BO3 not in allowed Biome or Smoothing area not in allowed Biome)" : "");
			        	    		OTG.log(LogMarker.INFO, "Rolling back X" + branchDataItem.Branch.getChunkX() + " Z" + branchDataItem.Branch.getChunkZ() + " Y" + branchDataItem.Branch.getY() + " " + branchDataItem.Branch.BO3Name + ":" + branchDataItem.Branch.getRotation() + allParentsString + " because required branch "+ childBranchDataItem.Branch.BO3Name + " couldn't spawn. Reason: " + reason);
			            		}

		            			rollBackBranch(branchDataItem, minimumSize, spawningRequiredBranchesOnly);
		            			bBreak = true;
			        		} else {
				        		// if this child branch could not spawn then in some cases other child branches won't be able to either
				        		// mark those child branches so they dont try to spawn and roll back the whole branch if a required branch can't spawn
				        		for(BranchDataItem childBranchDataItem2 : branchDataItem.getChildren(false))
				        		{
				        			if(!wasntBelowOther || !spawnedBranchLastCycle)
				        			{
					        			if(
				        					childBranchDataItem == childBranchDataItem2 ||
					        				(
				        						!(childBranchDataItem2.CannotSpawn || childBranchDataItem2.DoneSpawning) &&
				        						(
			        								(
		        										childBranchDataItem.Branch.getY() < 0 ||
		        										chunkIsIneligible ||
		        										(wasntBelowOther && ((BO3)childBranchDataItem2.Branch.getObject()).getSettings().mustBeBelowOther) ||
		        										(wasntOnWater && ((BO3)childBranchDataItem2.Branch.getObject()).getSettings().SpawnOnWaterOnly) ||
		        										(wasOnWater && !((BO3)childBranchDataItem2.Branch.getObject()).getSettings().CanSpawnOnWater)
			        								) &&
			        								childBranchDataItem.Branch.getX() == childBranchDataItem2.Branch.getX() &&
			        								childBranchDataItem.Branch.getY() == childBranchDataItem2.Branch.getY() &&
			        								childBranchDataItem.Branch.getZ() == childBranchDataItem2.Branch.getZ()
					        					)
				        					)
			        					)
					        			{
					        				childBranchDataItem2.DoneSpawning = true;
					        				childBranchDataItem2.CannotSpawn = true;

							        		branchGroupFailedSpawning = false;
							        		if(childBranchDataItem2.Branch.isRequiredBranch)
							        		{
							        			branchGroupFailedSpawning = true;

							        	    	// Check if there are any more required branches in this group that haven't tried to spawn yet.
								        		for(BranchDataItem childBranchDataItem3 : branchDataItem.getChildren(false))
								        		{
								        			if(
							        					childBranchDataItem3 != childBranchDataItem2 &&
							    						(childBranchDataItem2.Branch.branchGroup != null && childBranchDataItem2.Branch.branchGroup.length() >= 0) &&
							    						childBranchDataItem2.Branch.branchGroup.equals(childBranchDataItem3.Branch.branchGroup) &&
							    						childBranchDataItem3.Branch.isRequiredBranch &&
								        				!childBranchDataItem3.DoneSpawning &&
								        				!childBranchDataItem3.CannotSpawn
						        					)
								        			{
								        				branchGroupFailedSpawning = false;
								        				break;
						        					}
								        		}
							        		}

					        				if(branchGroupFailedSpawning && !collidedWithParentOrSibling)
					        				{
							            		if(OTG.getPluginConfig().spawnLog)
							            		{
							        	    		String allParentsString = "";
							        	    		BranchDataItem tempBranch = branchDataItem;
							        	    		while(tempBranch.Parent != null)
							        	    		{
							        	    			allParentsString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ() + " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
							        	    			tempBranch = tempBranch.Parent;
							        	    		}

							        	    		String occupiedByObjectsString = "";
							        	    		if(spaceIsOccupied)
							        	    		{
							        	    			for(BranchDataItem collidingObject : collidingObjects)
							        	    			{
							        	    				String occupiedByObjectString = collidingObject.Branch.BO3Name + ":" + collidingObject.Branch.getRotation() + " X" + collidingObject.Branch.getChunkX() + " Z" + collidingObject.Branch.getChunkZ() + " Y" + collidingObject.Branch.getY();
									        	    		tempBranch = collidingObject;
									        	    		while(tempBranch.Parent != null)
									        	    		{
									        	    			occupiedByObjectString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ()+ " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
									        	    			tempBranch = tempBranch.Parent;
									        	    		}
									        	    		occupiedByObjectsString += " " + occupiedByObjectString;
							        	    			}
							        	    		}

							        	    		String reason =
							        	    				(branchFrequencyGroupsNotPassed ? "BranchFrequencyGroupNotPassed " : "") +
							        	    				(branchFrequencyNotPassed ? "BranchFrequencyNotPassed " : "") +
							        	    				(!isInsideWorldBorder ? "IsOutsideWorldBorder " : "") +
							        	    				(!startChunkBlockChecksPassed ? "StartChunkBlockChecksNotPassed " : "") +
							        	    				(collidedWithParentOrSibling ? "CollidedWithParentOrSibling " : "") +
							        	    				(wasntBelowOther ? "WasntBelowOther " : "") +
							        	    				(wasntInsideOther ? "WasntInsideOther " : "") +
							        	    				(cannotSpawnInsideOther ? "CannotSpawnInsideOther " : "") +
							        	    				(wasntOnWater ? "WasntOnWater " : "") +
							        	    				(wasOnWater ? "WasOnWater " : "") +
							        	    				(childBranchDataItem.Branch.getY() < 0 ? " WasBelowY0 " : "") +
							        	    				(!branchFrequencyGroupsNotPassed && !branchFrequencyNotPassed && isInsideWorldBorder && startChunkBlockChecksPassed && !wasntBelowOther && !cannotSpawnInsideOther && !wasntOnWater && !wasOnWater && !wasntBelowOther && !chunkIsIneligible && spaceIsOccupied ? "SpaceIsOccupied by" + occupiedByObjectsString : "") + (wasntBelowOther ? "WasntBelowOther " : "") + (chunkIsIneligible ? "ChunkIsIneligible: Either the chunk is occupied by another structure or a default structure, or the BO3/smoothing area is not allowed in the Biome)" : "");
							        	    		OTG.log(LogMarker.INFO, "Rolling back X" + branchDataItem.Branch.getChunkX() + " Z" + branchDataItem.Branch.getChunkZ() + " Y" + branchDataItem.Branch.getY() + " " + branchDataItem.Branch.BO3Name + ":" + branchDataItem.Branch.getRotation() + allParentsString + " because required branch "+ childBranchDataItem.Branch.BO3Name + " couldn't spawn. Reason: " + reason);
							            		}
						            			rollBackBranch(branchDataItem, minimumSize, spawningRequiredBranchesOnly);
						            			bBreak = true;
						            			break;
					        				}
					        			}
				        			}
				        		}
			        		}
			        		if(bBreak)
			        		{
			        			break;
			        		}
		        		}
		        	}
	        	}
	        	else if(childBranchDataItem.SpawnDelayed)
	        	{
	        		childBranchDataItem.SpawnDelayed = false;
	        	}
	        }

    		// when spawning optional branches spawn them first then traverse any previously spawned required branches
	        // When calling AddBranches during a rollback to continue spawning a branch group don't traverse already spawned children (otherwise the branch could spawn children more than once per cycle).
	        if(
        		!traverseOnlySpawnedChildren &&
        		!spawningRequiredBranchesOnly &&
        		!branchDataItem.CannotSpawn
    		)
	        {
	        	for(BranchDataItem childBranchDataItem : branchDataItem.getChildren(false))
	        	{
	        		if(AllBranchesBranchDataHash.contains(childBranchDataItem.branchNumber))
	        		{
						if(
							(
								childBranchDataItem.Branch.isRequiredBranch ||
								(
									SpawningCanOverrideBranches &&
									!((BO3)childBranchDataItem.Branch.getObject()).getSettings().canOverride
								)
							) &&
							!childBranchDataItem.CannotSpawn &&
							(
								!childBranchDataItem.SpawnDelayed ||
								!spawnedBranchLastCycle
							)
						)
						{
							traverseAndSpawnChildBranches(childBranchDataItem, minimumSize, spawningRequiredBranchesOnly);
						}
	        		}
	        	}
	        }

	        // When calling AddBranches during a rollback to continue spawning a branch group don't traverse already spawned children (otherwise the branch could spawn children more than once per cycle).
	        if(
        		!traverseOnlySpawnedChildren &&
        		spawningRequiredBranchesOnly &&
        		!branchDataItem.CannotSpawn
    		)
	        {
	        	for(BranchDataItem childBranchDataItem : branchDataItem.getChildren(false))
	        	{
	        		if(AllBranchesBranchDataHash.contains(childBranchDataItem.branchNumber))
	        		{
						if(childBranchDataItem.Branch.isRequiredBranch)
						{
							traverseAndSpawnChildBranches(childBranchDataItem, minimumSize, spawningRequiredBranchesOnly);
						}
	        		}
	        	}
	        }
    	}
    }

    private boolean checkBranchFrequency(BranchDataItem childBranchDataItem, BO3 bo3)
    {
    	boolean branchFrequencyPassed = true;
        // Check if no other branch of the same type (filename) is within the minimum radius (branch frequency)
		int radius = bo3.getSettings().branchFrequency;
		if(radius > 0)
		{
			float distanceBetweenBranches = 0;
			
			ArrayList<ChunkCoordinate> chunkCoords = AllBranchesBranchDataByName.get(bo3.getName());
			if(chunkCoords != null)
			{
            	// Check BO3 frequency
       			for(ChunkCoordinate cachedChunk : chunkCoords)
    			{
                    // Find distance between two points
       				distanceBetweenBranches = (int)Math.floor(Math.sqrt(Math.pow(childBranchDataItem.ChunkCoordinate.getChunkX() - cachedChunk.getChunkX(), 2) + Math.pow(childBranchDataItem.ChunkCoordinate.getChunkZ() - cachedChunk.getChunkZ(), 2)));
                    if (distanceBetweenBranches <= radius)
                    {
                    	// Other branch of the same type is too nearby, cannot spawn here!
                    	branchFrequencyPassed = false;
                        break;
                    }
    			}				
			}
		}
		return branchFrequencyPassed;
	}

    private boolean checkBranchFrequencyGroups(BranchDataItem childBranchDataItem, BO3 bo3)
    {	
    	boolean branchFrequencyGroupsPassed = true;
		// Check if no other branches that are a member of the same branch frequency group as this branch are within the minimum radius (branch group frequency)
		if(bo3.getSettings().branchFrequencyGroups.size() > 0)
		{
	    	int radius = bo3.getSettings().branchFrequency;
        	float distanceBetweenStructures = 0;
        	int cachedChunkRadius = 0;
        	ChunkCoordinate cachedChunk = null;
        	for(Entry<String, Integer> entry : bo3.getSettings().branchFrequencyGroups.entrySet())
        	{
        		HashMap<ChunkCoordinate, ArrayList<Integer>> spawnedStructure = AllBranchesBranchDataByGroup.get(entry.getKey());
        		if(spawnedStructure != null)
        		{
        			for(Entry<ChunkCoordinate, ArrayList<Integer>> cachedChunkEntry : spawnedStructure.entrySet())
        			{
        				cachedChunk = cachedChunkEntry.getKey();
        				cachedChunkRadius = 0;
        				for(Integer integer : cachedChunkEntry.getValue())
        				{
        					if(integer.intValue() > cachedChunkRadius)
        					{
        						cachedChunkRadius = integer.intValue();	
        					}
        				}
        				radius = entry.getValue().intValue() >= cachedChunkRadius ? entry.getValue().intValue() : cachedChunkRadius;
                        // Find distance between two points
        				distanceBetweenStructures = (int)Math.floor(Math.sqrt(Math.pow(childBranchDataItem.ChunkCoordinate.getChunkX() - cachedChunk.getChunkX(), 2) + Math.pow(childBranchDataItem.ChunkCoordinate.getChunkZ() - cachedChunk.getChunkZ(), 2)));
                        if (distanceBetweenStructures <= radius)
                        {
	    					// Branch with same branchFrequencyGroup was closer than branchFrequencyGroup's frequency in chunks, don't spawn
                        	branchFrequencyGroupsPassed = false;
                        	break;
                        }
        			}
        			if(!branchFrequencyGroupsPassed)
        			{
        				break;
        			}
        		}
        	}
		}
		return branchFrequencyGroupsPassed;
	}
    
	private boolean checkMustBeBelowOther(BranchDataItem childBranchDataItem)
    {
		// Check for mustBeBelowOther
		boolean bFoundOther = false;
		if(AllBranchesBranchDataByChunk.containsKey(childBranchDataItem.ChunkCoordinate))
		{
			for(BranchDataItem branchDataItem2 : AllBranchesBranchDataByChunk.get(childBranchDataItem.ChunkCoordinate))
			{
				if(
					branchDataItem2.ChunkCoordinate.equals(childBranchDataItem.ChunkCoordinate) &&
					!((BO3) branchDataItem2.Branch.getObject()).getSettings().canOverride &&
					branchDataItem2.Branch.getY() >= childBranchDataItem.Branch.getY()
				)
				{
					bFoundOther = true;
					break;
				}
			}
		}
		return bFoundOther;
	}

	/**
     * 
     * @param childBranchDataItem
     * @param bo3
     * @return True if the branch can spawn
     */
    private boolean checkCannotBeInside(BranchDataItem childBranchDataItem, BO3 bo3)
    {
		// Check for cannotSpawnInside
		boolean foundSpawnBlocker = false;
		if(AllBranchesBranchDataByChunk.containsKey(childBranchDataItem.ChunkCoordinate))
		{
			ArrayList<BranchDataItem> branchDataInChunk = AllBranchesBranchDataByChunk.get(childBranchDataItem.ChunkCoordinate);
			for(String cantBeInsideBO3 : bo3.getSettings().cannotBeInsideBranches)
			{
    			for(BranchDataItem branchDataItem3 : branchDataInChunk)
				{
					if(branchDataItem3 != childBranchDataItem && branchDataItem3 != childBranchDataItem.Parent)
					{
						for(String branchName : ((BO3)branchDataItem3.Branch.getObject()).getSettings().getInheritedBO3s()) // getInheritedBO3s also contains this BO3
						{
							if(branchName.equals(cantBeInsideBO3))
							{
   	    						if(checkCollision(childBranchDataItem.Branch, branchDataItem3.Branch))
   	    						{
   	     	        				if(OTG.getPluginConfig().spawnLog)
   	    	        				{
   	     	        					OTG.log(LogMarker.INFO, "CannotBeInside branch " + childBranchDataItem.Branch.BO3Name + " was blocked by " + branchDataItem3.Branch.BO3Name);
   	    	        				}
   	     	        				foundSpawnBlocker = true;
   	    							break;
   	    						}
							}
						}
   						if(foundSpawnBlocker)
   						{
   							break;
   						}
					}
				}
    			if(foundSpawnBlocker)
    			{
						break;
    			}
    		}
		}
		return !foundSpawnBlocker;
	}

	private boolean checkMustBeInside(BranchDataItem childBranchDataItem, BO3 bo3)
	{
		// Check for mustBeInside
		// Only one branch has to be present
		// TODO: Make AND/OR switch
		boolean foundSpawnRequirement = false;
		if(AllBranchesBranchDataByChunk.containsKey(childBranchDataItem.ChunkCoordinate))
		{		    	    			
			boolean allBranchesFound = false;
			ArrayList<BranchDataItem> branchDataInChunk = AllBranchesBranchDataByChunk.get(childBranchDataItem.ChunkCoordinate);
			for(String mustBeInsideBO3 : bo3.getSettings().mustBeInsideBranches)
			{
				boolean bFoundPart = false;
    			for(BranchDataItem branchDataItem3 : branchDataInChunk)
				{
						if(branchDataItem3 != childBranchDataItem && branchDataItem3 != childBranchDataItem.Parent)
						{
							for(String branchName : ((BO3)branchDataItem3.Branch.getObject()).getSettings().getInheritedBO3s()) // getInheritedBO3s also contains this BO3
							{
								if(branchName.equals(mustBeInsideBO3))
								{
	   	    						if(checkCollision(childBranchDataItem.Branch, branchDataItem3.Branch))
	   	    						{
	   	    							bFoundPart = true;
	   	    							break;
	   	    						}
								}
							}
   						if(bFoundPart)
   						{
   							break;
   						}
					}
				}
    			// TODO
    			if(bFoundPart)
    			{
    				allBranchesFound = true;
    				break;
    			}
    			//if(!bFoundPart)
    			{
    				//allBranchesFound = false;
					//break;
    			}
    		}
			if(allBranchesFound)
			{
				foundSpawnRequirement = true;
			}
		}
		return foundSpawnRequirement;
	}

	private void rollBackBranch(BranchDataItem branchData, boolean minimumSize, boolean spawningRequiredBranchesOnly)
    {
    	// When spawning an optional branch its required branches are spawned immediately as well (if there are no optional branches in the same branchGroup)
    	// This can cause a rollback if the required branches cannot spawn. Make sure that the parent branch of the optional branch isn't rolled back since it
    	// is currently still being processed and is spawning its optional branches.
    	if(spawningRequiredChildrenForOptionalBranch && currentSpawningRequiredChildrenForOptionalBranch.Parent == branchData)
    	{
    		return;
    	}

    	// Remove all children of this branch from AllBranchesBranchData
    	// And set this branches' CannotSpawn to true
    	// check if the parent has any required branches that cannot spawn
    	// and roll back until there is a viable branch pattern

    	branchData.CannotSpawn = true;
    	branchData.DoneSpawning = true;

    	branchData.wasDeleted = true;

    	branchData.isBeingRolledBack = true;
    	deleteBranchChildren(branchData,minimumSize, spawningRequiredBranchesOnly);

    	if(AllBranchesBranchDataHash.contains(branchData.branchNumber))
    	{
    		if(OTG.getPluginConfig().spawnLog)
    		{
	    		String allParentsString = "";
	    		BranchDataItem tempBranch = branchData;
	    		while(tempBranch.Parent != null)
	    		{
	    			allParentsString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ() + " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
	    			tempBranch = tempBranch.Parent;
	    		}
	    		OTG.log(LogMarker.INFO, "Deleted X" + branchData.Branch.getChunkX() + " Z" + branchData.Branch.getChunkZ() + " Y" + branchData.Branch.getY() + " " + branchData.Branch.BO3Name + ":" + branchData.Branch.getRotation()  + (branchData.Branch.isRequiredBranch ? " required" : " optional") + " cycle " + Cycle + allParentsString);
    		}

    		removeFromCaches(branchData);    	
    	}

    	if(!((BO3)branchData.Branch.getObject()).getSettings().canOverride)
    	{
	    	// If this branch is allowing lower-lying .mustBeBelowOther branches to spawn then roll those back as well

    		ArrayList<BranchDataItem> allBranchesBranchData2 = new ArrayList<BranchDataItem>();
    		ArrayList<BranchDataItem> branchDataByChunk = AllBranchesBranchDataByChunk.get(branchData.ChunkCoordinate);
    		if(branchDataByChunk != null)
    		{
	    		allBranchesBranchData2.addAll(branchDataByChunk);
	    		for(BranchDataItem branchDataItem2 : allBranchesBranchData2)
	    		{
	    			if(AllBranchesBranchDataHash.contains(branchDataItem2.branchNumber))
	    			{
		    			if(branchDataItem2 != branchData)
		    			{
			    			if(((BO3)branchDataItem2.Branch.getObject()).getSettings().mustBeBelowOther && branchDataItem2.ChunkCoordinate.equals(branchData.ChunkCoordinate))
			    			{
			    				boolean branchAboveFound = false;
			    				for(BranchDataItem branchDataItem3 : AllBranchesBranchDataByChunk.get(branchDataItem2.ChunkCoordinate))
		    					{
			    					if(
		    							branchDataItem3 != branchData &&
		    							!((BO3)branchDataItem3.Branch.getObject()).getSettings().mustBeBelowOther &&
		    							!((BO3)branchDataItem3.Branch.getObject()).getSettings().canOverride &&
		    							branchDataItem3.ChunkCoordinate.equals(branchDataItem2.ChunkCoordinate)
									)
			    					{
			    						if(branchDataItem3.Branch.getY() >= branchDataItem2.Branch.getY())
			    						{
			    							branchAboveFound = true;
			    							break;
			    						}
			    					}
		    					}
			    				if(!branchAboveFound)
			    				{
			    					rollBackBranch(branchDataItem2, minimumSize, spawningRequiredBranchesOnly);
			    				}
			    			}
		    			}
	    			}
	    		}
    		}
    	}

    	// If this branch is allowing mustBeInside branches to spawn then roll those back as well
    	ArrayList<BranchDataItem> allBranchesBranchData2 = new ArrayList<BranchDataItem>();
    	ArrayList<BranchDataItem> branchDataByChunk = AllBranchesBranchDataByChunk.get(branchData.ChunkCoordinate);
		if(branchDataByChunk != null)
		{
			allBranchesBranchData2.addAll(branchDataByChunk);
	    	for(BranchDataItem branchDataItem2 : allBranchesBranchData2)
	    	{
	    		if(AllBranchesBranchDataHash.contains(branchDataItem2.branchNumber))
	    		{
		    		if(branchDataItem2 != branchData)
					{
		    			if(
							((BO3)branchDataItem2.Branch.getObject()).getSettings().mustBeInsideBranches.size() > 0 &&
							branchDataItem2.ChunkCoordinate.equals(branchData.ChunkCoordinate)
						)
		    			{
							boolean currentBO3Found = false;
							for(String mustBeInsideBO3Name : ((BO3)branchDataItem2.Branch.getObject()).getSettings().mustBeInsideBranches)
							{
								for(String branchName : ((BO3)branchData.Branch.getObject()).getSettings().getInheritedBO3s())
								{
									if(branchName.equals(mustBeInsideBO3Name))
									{
										currentBO3Found = true;
										break;
									}
								}
								if(currentBO3Found)
								{
									break;
								}
							}
							// The BO3 that is currently being rolled back may have been allowing this mustBeInside branch to spawn
							if(currentBO3Found) 
							{
								// Check if the branch can remain spawned without the branch we're rolling back
	    	    				if(!checkMustBeInside(branchDataItem2, ((BO3)branchDataItem2.Branch.getObject())))
	    	    				{
	    	    					rollBackBranch(branchDataItem2, minimumSize, spawningRequiredBranchesOnly);
	    	    				}
							}
		    			}
					}
	    		}
	    	}
		}
		// if this branch is a required branch
		// then roll back the parent as well
		if(branchData.Parent != null && !branchData.Parent.isBeingRolledBack)
		{
    		if(branchData.Branch.isRequiredBranch)
    		{
    			//OTG.log(LogMarker.INFO, "RollBackBranch 4: " + branchData.Parent.Branch.BO3Name + " <> " + branchData.Branch.BO3Name);
    			rollBackBranch(branchData.Parent, minimumSize, spawningRequiredBranchesOnly);
    		} else {

    			// Mark for spawning the parent and all other branches in the same branch group that spawn after this branch (unless they have already been spawned successfully)
    			boolean parentDoneSpawning = true;
    			boolean currentBranchFound = false;
        		for (BranchDataItem branchDataItem2 : branchData.Parent.getChildren(false))
        		{
        			if(currentBranchFound)
        			{
        				if(
    						branchData.Branch.branchGroup != null && branchData.Branch.branchGroup.length() >= 0 &&
    						branchData.Branch.branchGroup.equals(branchDataItem2.Branch.branchGroup)
						)
        				{
	            			if(
            					!branchDataItem2.wasDeleted &&
            					!AllBranchesBranchDataHash.contains(branchDataItem2.branchNumber))
	            			{
	        					branchDataItem2.CannotSpawn = false;
	        					branchDataItem2.DoneSpawning = false;
	            			}
        				}
        			}
        			if(branchDataItem2 == branchData)
        			{
        				currentBranchFound = true;
        			}
        			if(!branchDataItem2.DoneSpawning && !branchDataItem2.CannotSpawn)
        			{
        				parentDoneSpawning = false;
        			}
        		}

        		// When rolling back after failing to spawn the required branches for an optional branch that just spawned don't roll back all the way to the optional
        		// branch's parent and continue spawning there. Instead only rollback up to the optional branch, then let the normal spawn cycle continue spawning the parent.
        		if(
    				!parentDoneSpawning &&
    				!(
						spawningRequiredChildrenForOptionalBranch &&
						currentSpawningRequiredChildrenForOptionalBranch == branchData
					)
				)
    			{
        			branchData.Parent.DoneSpawning = false;

	        		// Rollbacks only happen when:

        			if(!spawningRequiredChildrenForOptionalBranch)
        			{
        				if(spawningRequiredBranchesOnly)
        				{
                			// 1. The branch being rolled back has spawned all its required-only branch groups but not yet its optional branches and one of the required child branches
                			// (that spawn in the same cycle) failed to spawn one of its required children and is rolled back.
            				// AddBranches should be called for the parent of the branch being rolled back and its parent if a branch group failed to spawn (and so on).

                			// Since we're using SpawningRequiredBranchesOnly AddBranches can traverse all child branches without problems.
            				addBranches(branchData.Parent, minimumSize, false, spawningRequiredBranchesOnly);
        				} else {
        					// 2. During the second phase of a cycle branch groups with optional branches are spawned, the optional branches get a chance to spawn first, after that the
        					// required branches try to spawn, if that fails the branch is rolled back.
        					// 3. A branch was rolled back that was a requirement for another branch (mustbeinside/mustbebelowother), causing the other branch to be rolled back as well.

                			// Since we're not using SpawningRequiredBranchesOnly AddBranches should only traverse child branches for any branches that it spawns from the branch group its re-trying.
        					// Otherwise some branches may have the same children traversed multiple times in a single phase.
            				addBranches(branchData.Parent, minimumSize, true, spawningRequiredBranchesOnly);
        				}
        			} else {

        				// 4. While spawning required children for an optional branch (SpawningRequiredChildrenForOptionalBranch == true).
            			// AddBranches should be called only for children of the optional branch since they may have multiple required branches in the same branch group.

            			// In this case AddBranches should not set DoneSpawning to true on the branches (unless they have only required branches) to make sure that any optional
            			// branches are spawned in the second phase of the cycle.
        				if(!spawningRequiredBranchesOnly)
        				{
        					throw new RuntimeException();
        				}

        				spawningRequiredChildrenForOptionalBranch = false;
            			// Since we're using SpawningRequiredBranchesOnly AddBranches can traverse all child branches without problems.
        				addBranches(branchData.Parent, minimumSize, false, spawningRequiredBranchesOnly);
        				spawningRequiredChildrenForOptionalBranch = true;
        			}
    			}
    		}
		}

    	branchData.isBeingRolledBack = false;
    }

    private void deleteBranchChildren(BranchDataItem branchData, boolean minimumSize, boolean spawningRequiredBranchesOnly)
    {
    	// Remove all children of this branch from AllBranchesBranchData
    	Stack<BranchDataItem> children = branchData.getChildren(true);
        for(BranchDataItem branchDataItem : children)
        {
        	branchDataItem.CannotSpawn = true;
        	branchDataItem.DoneSpawning = true;
        	branchDataItem.wasDeleted = true;

        	if(branchDataItem.getChildren(true).size() > 0)
        	{
    			deleteBranchChildren(branchDataItem, minimumSize, spawningRequiredBranchesOnly);
        	}
        	if(AllBranchesBranchDataHash.contains(branchDataItem.branchNumber))
        	{
        		if(OTG.getPluginConfig().spawnLog)
        		{
	        		String allParentsString = "";
	        		BranchDataItem tempBranch = branchDataItem;
	        		while(tempBranch.Parent != null)
	        		{
	        			allParentsString += " <-- X" + tempBranch.Parent.Branch.getChunkX() + " Z" + tempBranch.Parent.Branch.getChunkZ() + " Y" + tempBranch.Parent.Branch.getY() + " " + tempBranch.Parent.Branch.BO3Name + ":" + tempBranch.Parent.Branch.getRotation();
	        			tempBranch = tempBranch.Parent;
	        		}

	        		OTG.log(LogMarker.INFO, "Deleted X" + branchDataItem.Branch.getChunkX() + " Z" + branchDataItem.Branch.getChunkZ() + " Y" + branchDataItem.Branch.getY() + " " + branchDataItem.Branch.BO3Name + ":" + branchDataItem.Branch.getRotation() + (branchDataItem.Branch.isRequiredBranch ? " required" : " optional") + " cycle " + Cycle + allParentsString);
        		}

        		removeFromCaches(branchDataItem);

	        	if(!((BO3)branchDataItem.Branch.getObject()).getSettings().canOverride)
	        	{
	    	    	// If this branch is allowing lower-lying .mustBeBelowOther branches to spawn then roll those back as well
	        		ArrayList<BranchDataItem> allBranchesBranchData2 = new ArrayList<BranchDataItem>();
	        		ArrayList<BranchDataItem> branchDataByChunk = AllBranchesBranchDataByChunk.get(branchDataItem.ChunkCoordinate);
	        		if(branchDataByChunk != null)
	        		{
		        		allBranchesBranchData2.addAll(branchDataByChunk);
		        		for(BranchDataItem branchDataItem2 : allBranchesBranchData2)
		        		{
		        			if(AllBranchesBranchDataHash.contains(branchDataItem2.branchNumber))
		        			{
			        			if(branchDataItem2 != branchDataItem)
			        			{
			    	    			if(((BO3)branchDataItem2.Branch.getObject()).getSettings().mustBeBelowOther && branchDataItem2.ChunkCoordinate.equals(branchDataItem.ChunkCoordinate))
			    	    			{
			    	    				boolean branchAboveFound = false;
			    	    				for(BranchDataItem branchDataItem3 : AllBranchesBranchDataByChunk.get(branchDataItem2.ChunkCoordinate))
		    	    					{
			    	    					if(
			        							branchDataItem3 != branchDataItem &&
			        							!((BO3)branchDataItem3.Branch.getObject()).getSettings().mustBeBelowOther &&
			        							!((BO3)branchDataItem3.Branch.getObject()).getSettings().canOverride &&
			        							branchDataItem3.ChunkCoordinate.equals(branchDataItem2.ChunkCoordinate)
			    							)
			    	    					{
			    	    						if(branchDataItem3.Branch.getY() >= branchDataItem2.Branch.getY())
			    	    						{
			    	    							branchAboveFound = true;
			    	    							break;
			    	    						}
			    	    					}
		    	    					}
			    	    				if(!branchAboveFound)
			    	    				{
			    	    					rollBackBranch(branchDataItem2, minimumSize, spawningRequiredBranchesOnly);
			    	    				}
			    	    			}
			        			}
		        			}
		        		}
	        		}
	        	}

	        	ArrayList<BranchDataItem> allBranchesBranchData2 = new ArrayList<BranchDataItem>();
        		ArrayList<BranchDataItem> branchDataByChunk = AllBranchesBranchDataByChunk.get(branchDataItem.ChunkCoordinate);
        		if(branchDataByChunk != null)
        		{
	        		allBranchesBranchData2.addAll(branchDataByChunk);
		        	// If this branch is allowing mustBeInside branches to spawn then roll those back as well
		        	for(BranchDataItem branchDataItem2 : allBranchesBranchData2)
		        	{
		        		if(AllBranchesBranchDataHash.contains(branchDataItem2.branchNumber))
		        		{
			        		if(branchDataItem2 != branchDataItem)
			    			{
			        			if(
			    					((BO3)branchDataItem2.Branch.getObject()).getSettings().mustBeInsideBranches.size() > 0 &&
			    					branchDataItem2.ChunkCoordinate.equals(branchDataItem.ChunkCoordinate)
			    				)
			        			{
									boolean currentBO3Found = false;
									for(String mustBeInsideBO3Name : ((BO3)branchDataItem2.Branch.getObject()).getSettings().mustBeInsideBranches)
									{
										for(String branchName : ((BO3)branchDataItem.Branch.getObject()).getSettings().getInheritedBO3s())
										{
											if(branchName.equals(mustBeInsideBO3Name))
											{
												currentBO3Found = true;
												break;
											}
										}
										if(currentBO3Found)
										{
											break;
										}
									}
									// The BO3 that is currently being rolled back may have been allowing this mustBeInside branch to spawn
									if(currentBO3Found) 
									{
										// Check if the branch can remain spawned without the branch we're rolling back
		    	    					if(!checkMustBeInside(branchDataItem2, ((BO3)branchDataItem2.Branch.getObject())))
			    	    				{
			    	    					rollBackBranch(branchDataItem2, minimumSize, spawningRequiredBranchesOnly);
			    	    				}
									}
			        			}
			    			}
		        		}
		        	}
        		}
        	}
        }
    }
    
    private void addToCaches(BranchDataItem branchData, BO3 bo3)
    {
    	AllBranchesBranchData.add(branchData);
    	AllBranchesBranchDataHash.add(branchData.branchNumber);
    	
		ArrayList<BranchDataItem> branchDataItemStack = AllBranchesBranchDataByChunk.get(branchData.ChunkCoordinate);
		if(branchDataItemStack != null)
		{
			branchDataItemStack.add(branchData);
		} else {
			branchDataItemStack = new ArrayList<BranchDataItem>();
			branchDataItemStack.add(branchData);
			AllBranchesBranchDataByChunk.put(branchData.ChunkCoordinate, branchDataItemStack);
		}

		ArrayList<ChunkCoordinate> sameNameBo3s = AllBranchesBranchDataByName.get(branchData.Branch.BO3Name);
		if(sameNameBo3s == null)
		{
			sameNameBo3s = new ArrayList<ChunkCoordinate>();
			AllBranchesBranchDataByName.put(branchData.Branch.BO3Name, sameNameBo3s);
		}
		sameNameBo3s.add(branchData.ChunkCoordinate);
			
		// Get branch groups
		for(Entry<String, Integer> entry : bo3.getSettings().branchFrequencyGroups.entrySet())
		{
			HashMap<ChunkCoordinate, ArrayList<Integer>> branchGroupInfo = AllBranchesBranchDataByGroup.get(entry.getKey());
			if(branchGroupInfo == null)
			{
				branchGroupInfo = new HashMap<ChunkCoordinate, ArrayList<Integer>>();
				AllBranchesBranchDataByGroup.put(entry.getKey(), branchGroupInfo);
			}
			ArrayList<Integer> branchGroupFrequency = branchGroupInfo.get(branchData.ChunkCoordinate);
			if(branchGroupFrequency == null)
			{
				branchGroupFrequency = new ArrayList<Integer>();
				branchGroupFrequency.add(entry.getValue());
				branchGroupInfo.put(branchData.ChunkCoordinate, branchGroupFrequency);
			} else {
				branchGroupFrequency.add(entry.getValue());
			}
		}
    }
    
	private void removeFromCaches(BranchDataItem branchDataItem)
	{		
		AllBranchesBranchData.remove(branchDataItem);
		AllBranchesBranchDataHash.remove(branchDataItem.branchNumber);
		ArrayList<BranchDataItem> branchDataItemStack = AllBranchesBranchDataByChunk.get(branchDataItem.ChunkCoordinate);
		if(branchDataItemStack != null)
		{
			branchDataItemStack.remove(branchDataItem);
			if(branchDataItemStack.size() == 0)
			{
				AllBranchesBranchDataByChunk.remove(branchDataItem.ChunkCoordinate);
			}
			ArrayList<ChunkCoordinate> allCoordsForBo3 = AllBranchesBranchDataByName.get(branchDataItem.Branch.BO3Name);
			allCoordsForBo3.remove(branchDataItem.ChunkCoordinate);
			if(allCoordsForBo3.size() == 0)
			{
				AllBranchesBranchDataByName.remove(branchDataItem.Branch.BO3Name);
			}
			for(Entry<String, Integer> entry : ((BO3)branchDataItem.Branch.getObject()).getSettings().branchFrequencyGroups.entrySet())
			{
				HashMap<ChunkCoordinate, ArrayList<Integer>> branchesByGroup = AllBranchesBranchDataByGroup.get(entry.getKey());
				ArrayList<Integer> frequenciesForGroupAtChunk = branchesByGroup.get(branchDataItem.ChunkCoordinate);
				frequenciesForGroupAtChunk.remove(entry.getValue());
				if(frequenciesForGroupAtChunk.size() == 0)
				{
					branchesByGroup.remove(branchDataItem.ChunkCoordinate);
					if(branchesByGroup.size() == 0)
					{
						AllBranchesBranchDataByGroup.remove(entry.getKey());
					}
				}
			}
		}
	}

    private Stack<BranchDataItem> checkSpawnRequirementsAndCollisions(BranchDataItem branchData, boolean minimumSize)
    {
    	// collidingObjects are only used for size > 0 check and to see if this branch tried to spawn on top of its parent
    	Stack<BranchDataItem> collidingObjects = new Stack<BranchDataItem>();
    	boolean bFound = false;

    	CustomObjectCoordinate coordObject = branchData.Branch;

    	if(!minimumSize)
    	{
		    // Check if any other structures in world are in this chunk
		    if(!bFound && (World.isInsidePregeneratedRegion(branchData.ChunkCoordinate) || World.getStructureCache().structureCache.containsKey(branchData.ChunkCoordinate)))
		    {
		    	collidingObjects.add(null);
		    	bFound = true;
		    }

		    // Check if the structure can spawn in this biome
		    if(!bFound && !IsStructureAtSpawn)
		    {
		    	ArrayList<String> biomeStructures;

            	LocalBiome biome3 = World.getBiome(branchData.ChunkCoordinate.getChunkX() * 16 + 8, branchData.ChunkCoordinate.getChunkZ() * 16 + 8);
                BiomeConfig biomeConfig3 = biome3.getBiomeConfig();
                // Get Bo3's for this biome
                ArrayList<String> structuresToSpawn = new ArrayList<String>();
                for (CustomStructureGen res : biomeConfig3.getCustomStructures())
                {
            		for(String bo3Name : res.objectNames)
            		{
            			structuresToSpawn.add(bo3Name);
            		}
                }

                biomeStructures = structuresToSpawn;

                boolean canSpawnHere = false;
                for(String structureToSpawn : biomeStructures)
                {
                	if(structureToSpawn.equals(Start.getObject().getName()))
                	{
                		canSpawnHere = true;
                		break;
                	}
                }

                if(!canSpawnHere)
				{
                	collidingObjects.add(null);
                	bFound = true;
				}
		    }

	    	int smoothRadius = ((BO3)Start.getObject()).getSettings().smoothRadius; // For collision detection use Start's SmoothingRadius. TODO: Improve this and use smoothingradius of individual branches?
	    	if(smoothRadius == -1 || ((BO3)coordObject.getObject()).getSettings().smoothRadius == -1)
	    	{
	    		smoothRadius = 0;
	    	}
	    	if(smoothRadius > 0 && !bFound)
	        {
	        	// get all chunks within smoothRadius and check structureCache for collisions
	    		double radiusInChunks = Math.ceil((smoothRadius) / (double)16);
	        	for(int x = branchData.ChunkCoordinate.getChunkX() - (int)radiusInChunks; x <= branchData.ChunkCoordinate.getChunkX() + radiusInChunks; x++)
	        	{
	            	for(int z = branchData.ChunkCoordinate.getChunkZ() - (int)radiusInChunks; z <= branchData.ChunkCoordinate.getChunkZ() + radiusInChunks; z++)
	            	{
	            		double distanceBetweenStructures = Math.floor((float) Math.sqrt(Math.pow(branchData.ChunkCoordinate.getChunkX() - x, 2) + Math.pow(branchData.ChunkCoordinate.getChunkZ() - z, 2)));
	            		if(distanceBetweenStructures <= radiusInChunks)
	            		{
	            		    // Check if any other structures in world are in this chunk
	            			if(World.isInsidePregeneratedRegion(ChunkCoordinate.fromChunkCoords(x,z)) || World.getStructureCache().structureCache.containsKey(ChunkCoordinate.fromChunkCoords(x,z)))
	            		    {
	            		        // Structures' bounding boxes are overlapping, don't add this branch.
	            		    	collidingObjects.add(null);
	            		    	bFound = true;
	            		    	break;
	            		    }

	            			if(!IsStructureAtSpawn)
	            			{
		            		    // Check if the structure can spawn in this biome
		            			ArrayList<String> biomeStructures;

	        	            	LocalBiome biome3 = World.getBiome(x * 16 + 8, z * 16 + 8);
	        	                BiomeConfig biomeConfig3 = biome3.getBiomeConfig();
	        	                // Get Bo3's for this biome
	        	                ArrayList<String> structuresToSpawn = new ArrayList<String>();
	        	                for (CustomStructureGen res : biomeConfig3.getCustomStructures())
	        	                {
	        	            		for(String bo3Name : res.objectNames)
	        	            		{
	        	            			structuresToSpawn.add(bo3Name);
	        	            		}
	        	                }

	        	                biomeStructures = structuresToSpawn;

		                        boolean canSpawnHere = false;
		                        for(String structureToSpawn : biomeStructures)
		                        {
		                        	if(structureToSpawn.equals(Start.getObject().getName()))
		                        	{
		                        		canSpawnHere = true;
		                        		break;
		                        	}
		                        }

		                        if(!canSpawnHere)
		        				{
		                        	collidingObjects.add(null);
		                        	bFound = true;
		                        	break;
		        				}
	            			}
	            		}
	            	}
	            	if(bFound)
	            	{
	            		break;
	            	}
	        	}
	        }
    	}

        if(!bFound && !((BO3) coordObject.getObject()).getSettings().canOverride)
        {
	        Stack<BranchDataItem> existingBranches = new Stack<BranchDataItem>();
	        if(AllBranchesBranchDataByChunk.containsKey(branchData.ChunkCoordinate))
	        {
	        	for(BranchDataItem existingBranchData : AllBranchesBranchDataByChunk.get(branchData.ChunkCoordinate))
		        {
		        	if(branchData.ChunkCoordinate.equals(existingBranchData.ChunkCoordinate) && !((BO3)existingBranchData.Branch.getObject()).getSettings().canOverride)
		        	{
		        		existingBranches.add(existingBranchData);
		        	}
		        }
	        }

	        if (existingBranches.size() > 0)
	        {
	        	for (BranchDataItem cachedBranch : existingBranches)
	        	{
	        		if(checkCollision(coordObject, cachedBranch.Branch))
	        		{
	        			collidingObjects.add(cachedBranch);
	        		}
	        	}
	        }
        }

    	return collidingObjects;
    }

    // TODO: return list with colliding structures instead of bool?
    private boolean checkCollision(CustomObjectCoordinate branchData1Branch, CustomObjectCoordinate branchData2Branch)
    {
    	if(
			!((BO3)branchData1Branch.getObject()).isCollidable() ||
			!((BO3)branchData2Branch.getObject()).isCollidable()
		)
    	{
    		return false;
    	}

    	// minX/maxX/minZ/maxZ are always positive.

        CustomObjectCoordinate branchData1BranchMinRotated = CustomObjectCoordinate.getRotatedBO3CoordsJustified(((BO3)branchData1Branch.getObject()).getSettings().getminX(), ((BO3)branchData1Branch.getObject()).getSettings().getminY(), ((BO3)branchData1Branch.getObject()).getSettings().getminZ(), branchData1Branch.getRotation());
        CustomObjectCoordinate branchData1BranchMaxRotated = CustomObjectCoordinate.getRotatedBO3CoordsJustified(((BO3)branchData1Branch.getObject()).getSettings().getmaxX(),((BO3)branchData1Branch.getObject()).getSettings().getmaxY(), ((BO3)branchData1Branch.getObject()).getSettings().getmaxZ(), branchData1Branch.getRotation());

        int startX = branchData1Branch.getX() + Math.min(branchData1BranchMinRotated.getX(),branchData1BranchMaxRotated.getX());
        int endX = branchData1Branch.getX() + Math.max(branchData1BranchMinRotated.getX(),branchData1BranchMaxRotated.getX());
        int startY = branchData1Branch.getY() + Math.min(branchData1BranchMinRotated.getY(),branchData1BranchMaxRotated.getY());
        int endY = branchData1Branch.getY() + Math.max(branchData1BranchMinRotated.getY(),branchData1BranchMaxRotated.getY());
        int startZ = branchData1Branch.getZ() + Math.min(branchData1BranchMinRotated.getZ(),branchData1BranchMaxRotated.getZ());
        int endZ = branchData1Branch.getZ() + Math.max(branchData1BranchMinRotated.getZ(),branchData1BranchMaxRotated.getZ());

        CustomObjectCoordinate branchData2BranchMinRotated = CustomObjectCoordinate.getRotatedBO3CoordsJustified(((BO3)branchData2Branch.getObject()).getSettings().getminX(), ((BO3)branchData2Branch.getObject()).getSettings().getminY(), ((BO3)branchData2Branch.getObject()).getSettings().getminZ(), branchData2Branch.getRotation());
        CustomObjectCoordinate branchData2BranchMaxRotated = CustomObjectCoordinate.getRotatedBO3CoordsJustified(((BO3)branchData2Branch.getObject()).getSettings().getmaxX(), ((BO3) branchData2Branch.getObject()).getSettings().getmaxY(), ((BO3)branchData2Branch.getObject()).getSettings().getmaxZ(), branchData2Branch.getRotation());

        int cachedBranchStartX = branchData2Branch.getX() + Math.min(branchData2BranchMinRotated.getX(),branchData2BranchMaxRotated.getX());
        int cachedBranchEndX = branchData2Branch.getX() + Math.max(branchData2BranchMinRotated.getX(),branchData2BranchMaxRotated.getX());
        int cachedBranchStartY = branchData2Branch.getY() + Math.min(branchData2BranchMinRotated.getY(),branchData2BranchMaxRotated.getY());
        int cachedBranchEndY = branchData2Branch.getY() + Math.max(branchData2BranchMinRotated.getY(),branchData2BranchMaxRotated.getY());
        int cachedBranchStartZ = branchData2Branch.getZ() + Math.min(branchData2BranchMinRotated.getZ(),branchData2BranchMaxRotated.getZ());
        int cachedBranchEndZ = branchData2Branch.getZ() + Math.max(branchData2BranchMinRotated.getZ(),branchData2BranchMaxRotated.getZ());

        if (
    		cachedBranchEndX >= startX &&
    		cachedBranchStartX <= endX &&
    		cachedBranchEndY >= startY &&
    		cachedBranchStartY <= endY &&
    		cachedBranchEndZ >= startZ &&
    		cachedBranchStartZ <= endZ
		)
        {
            // Structures' bounding boxes are overlapping
            return true;
        }

    	return false;
    }

    /**
     * Add the object to the list of BO3's to be spawned for this chunk
     * @param coordObject
     * @param chunkCoordinate
     */
    private void addToChunk(CustomObjectCoordinate coordObject, ChunkCoordinate chunkCoordinate, Map<ChunkCoordinate, Stack<CustomObjectCoordinate>> objectList)
    {
    	//OTG.log(LogMarker.INFO, "AddToChunk X" + chunkCoordinate.getChunkX() + " Z" + chunkCoordinate.getChunkZ());

        // Get the set of structures to spawn that is currently being stored
        // for the target chunk or create a new one if none exists
        Stack<CustomObjectCoordinate> objectsInChunk = objectList.get(chunkCoordinate);
        if (objectsInChunk == null)
        {
            objectsInChunk = new Stack<CustomObjectCoordinate>();
        }
    	// Add the structure to the set
    	objectsInChunk.add(coordObject);
        objectList.put(chunkCoordinate, objectsInChunk);
    }

    // This method gets called by other chunks spawning their structures to
    // finish any branches going to this chunk
    /**
    * Checks if this structure or any of its branches are inside the given
    * chunk and spawns all objects that are including their smoothing areas (if any)
    *
    * @param chunkCoordinate
    */
    public boolean spawnForChunkOTGPlus(ChunkCoordinate chunkCoordinate)
    {
    	//OTG.log(LogMarker.INFO, "SpawnForChunk X" + chunkCoordinate.getChunkX() + " Z" + chunkCoordinate.getChunkZ() + " " + Start.BO3Name);

        // If this structure is not allowed to spawn because a structure
        // of the same type (this.Start BO3 filename) has already been
        // spawned nearby.
    	if(Start == null)
    	{
			throw new RuntimeException();
    	}
    	if ((!ObjectsToSpawn.containsKey(chunkCoordinate) && !SmoothingAreasToSpawn.containsKey(chunkCoordinate)))
        {
            return true;
        }

    	doStartChunkBlockChecks();

        // Get all BO3's that should spawn in the given chunk, if any
        // Note: The given chunk may not necessarily be the chunkCoordinate of this.Start
        Stack<CustomObjectCoordinate> objectsInChunk = ObjectsToSpawn.get(chunkCoordinate);
        if (objectsInChunk != null)
        {
        	BO3Config config = ((BO3)Start.getObject()).getSettings();
            LocalBiome biome = null;
            BiomeConfig biomeConfig = null;
            if(config.SpawnUnderWater)
        	{
            	biome = World.getBiome(Start.getX() + 8, Start.getZ() + 7);
            	biomeConfig = biome.getBiomeConfig();
            	if(biomeConfig == null)
            	{
            		throw new RuntimeException(); // TODO: Remove after testing
            	}
        	}

            // Do ReplaceAbove / ReplaceBelow
            for (CustomObjectCoordinate coordObject : objectsInChunk)
            {
                if (coordObject.isSpawned)
                {
                    continue;
                }

                BO3 bo3 = ((BO3)coordObject.getObject());
                if(bo3 == null)
                {
                	throw new RuntimeException(); // TODO: Remove after testing
                }

                BO3Config objectConfig = bo3.getSettings();

                if (!coordObject.spawnWithChecks(chunkCoordinate, World, Random, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceAbove : objectConfig.replaceAbove, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceBelow : objectConfig.replaceBelow, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceWithBiomeBlocks : objectConfig.replaceWithBiomeBlocks, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceWithSurfaceBlock : objectConfig.replaceWithSurfaceBlock, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceWithGroundBlock : objectConfig.replaceWithGroundBlock, config.SpawnUnderWater,  !config.SpawnUnderWater ? -1 : (biomeConfig.useWorldWaterLevel ? World.getConfigs().getWorldConfig().waterLevelMax : biomeConfig.waterLevelMax), false, true))
                {
                	OTG.log(LogMarker.FATAL, "Could not spawn chunk " + coordObject.BO3Name + " for structure " + Start.getObject().getName());
                	throw new RuntimeException("Could not spawn chunk " + coordObject.BO3Name + " for structure " + Start.getObject().getName());
                }
            }

            // Spawn smooth areas in this chunk if any exist
            // If SpawnSmoothAreas returns false then spawning has
            // been delayed and should be tried again later.
        	if(!smoothingAreaManager.spawnSmoothAreas(chunkCoordinate, SmoothingAreasToSpawn, Start, World))
        	{
        		BO3.OriginalTopBlocks.clear(); // TODO: Make this prettier
        		return false;
    		}

            for (CustomObjectCoordinate coordObject : objectsInChunk)
            {
                if (coordObject.isSpawned)
                {
                    continue;
                }

                BO3 bo3 = ((BO3)coordObject.getObject());
                if(bo3 == null)
                {
                	throw new RuntimeException(); // TODO: Remove this after testing
                }

                BO3Config objectConfig = bo3.getSettings();

                if (!coordObject.spawnWithChecks(chunkCoordinate, World, Random, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceAbove : objectConfig.replaceAbove, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceBelow : objectConfig.replaceBelow, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceWithBiomeBlocks : objectConfig.replaceWithBiomeBlocks, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceWithSurfaceBlock : objectConfig.replaceWithSurfaceBlock, config.overrideChildSettings && objectConfig.overrideChildSettings ? config.replaceWithGroundBlock : objectConfig.replaceWithGroundBlock, config.SpawnUnderWater,  !config.SpawnUnderWater ? -1 : (biomeConfig.useWorldWaterLevel ? World.getConfigs().getWorldConfig().waterLevelMax : biomeConfig.waterLevelMax), false, false))
                //if(1 == 0)
                {
                	OTG.log(LogMarker.FATAL, "Could not spawn chunk " + coordObject.BO3Name + " for structure " + Start.getObject().getName());
                	throw new RuntimeException("Could not spawn chunk " + coordObject.BO3Name + " for structure " + Start.getObject().getName());
                } else {

                	this.modDataManager.spawnModData(objectConfig, coordObject, chunkCoordinate);
                	this.spawnerManager.spawnSpawners(objectConfig, coordObject, chunkCoordinate);
                	this.particlesManager.spawnParticles(objectConfig, coordObject, chunkCoordinate);
                	this.entitiesManager.spawnEntities(this.World, objectConfig, coordObject, chunkCoordinate);
                    coordObject.isSpawned = true;
                }
            }
        } else {
            // Spawn smooth areas in this chunk if any exist
            // If SpawnSmoothAreas returns false then spawning has
            // been delayed and should be tried again later.
        	if(!smoothingAreaManager.spawnSmoothAreas(chunkCoordinate, SmoothingAreasToSpawn, Start, World))
        	{
        		BO3.OriginalTopBlocks.clear(); // TODO: Make this prettier
        		return false;
    		}
        }

		ObjectsToSpawn.remove(chunkCoordinate);
		SmoothingAreasToSpawn.remove(chunkCoordinate);	
        BO3.OriginalTopBlocks.clear(); // TODO: Make this prettier		
        return true;
    }

    // OTG

    private void addBranches(CustomObjectCoordinate coordObject, int depth)
    {
    	// This should never happen for OTG+

    	CustomObject object = coordObject.getObject();

    	if(object != null)
    	{
	        for (Branch branch : getBranches(object, coordObject.getRotation()))
	        {
	        	// TODO: Does passing null as startbo3name work?
	            CustomObjectCoordinate childCoordObject = branch.toCustomObjectCoordinate(World, Random, coordObject.getRotation(), coordObject.getX(), coordObject.getY(), coordObject.getZ(), null);

	            // Don't add null objects
	            if (childCoordObject == null)
	            {
	                continue;
	            }

	            // Add this object to the chunk
	            addToSpawnList(childCoordObject, object);

	            // Also add the branches of this object
	            if (depth < maxBranchDepth)
	            {
	                addBranches(childCoordObject, depth + 1);
	            }
	        }
    	}
    }

    private Branch[] getBranches(CustomObject customObject, Rotation rotation)
    {
        return ((BO3)customObject).getBranches(rotation);
    }

    /**
     * Adds the object to the spawn list of each chunk that the object
     * touches.
     * @param coordObject The object.
     */
    private void addToSpawnList(CustomObjectCoordinate coordObject, CustomObject parent)
    {
        ChunkCoordinate chunkCoordinate = coordObject.getPopulatingChunk();
        if(chunkCoordinate != null)
        {
	        Set<CustomObjectCoordinate> objectsInChunk = objectsToSpawn.get(chunkCoordinate);
	        if (objectsInChunk == null)
	        {
	            objectsInChunk = new LinkedHashSet<CustomObjectCoordinate>();
	            objectsToSpawn.put(chunkCoordinate, objectsInChunk);
	        }
	        objectsInChunk.add(coordObject);
        } else {
    		if(OTG.getPluginConfig().spawnLog)
    		{
	    		OTG.log(LogMarker.WARN, "Error reading branch in BO3 " + parent.getName()  + " Could not find BO3: " + coordObject.BO3Name);
    		}
        }
    }

    public void spawnForChunk(ChunkCoordinate chunkCoordinate)
    {
        Set<CustomObjectCoordinate> objectsInChunk = objectsToSpawn.get(chunkCoordinate);
        if (objectsInChunk != null)
        {
            for (CustomObjectCoordinate coordObject : objectsInChunk)
            {
                coordObject.spawnWithChecks(this, World, height, Random);
            }
        }
    }
}
