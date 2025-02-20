package models

import java.io.File
import scala.io.Source

object CharacterData {

  def positiveData(data: Map[String, String]): List[String] = {
    val keys = data.keys.toList
    keys.filter(key => data.get(key) == Some("on"))
  }

  def parse(data: Map[String, String], gameData: GameData, customIconic: Option[File], customAnimalIconic: Option[File], customLogo: Option[File]): CharacterData = {
    //val data2 = data.flatMap { case (key, list) => key -> list.headOption }
    //println("Data 2 "+data2)
    val positive = positiveData(data)

    // classes
    val basicClassNames = positive
      .filter(_.startsWith("class-"))
      .map(_.substring(6)).toList
    val switchClassNames = data
      .filter(_._1.startsWith("switch-"))
      .filter(_._2 != "")
      .map(_._2).toList

    val classNames = basicClassNames ::: switchClassNames

    val baseClasses: List[BaseClass] = classNames.flatMap(name => gameData.classByName(name)).toList
    
    val classes: List[GameClass] = baseClasses.map { cls =>
      if (cls.axes.isEmpty)
        data.get("variant-"+cls.name) match {
          case Some(variantName) => 
            //println("Variant name "+variantName)
            //println("Variant found "+cls.variantByName(variantName))
            cls.variantByName(variantName).getOrElse(cls)
          case _ => cls
        }
      else {
        val axisValues = (Range(0, cls.axes.length) flatMap { i => data.get("variant-"+cls.name+"-axis-"+i) }).toList
        println("Axis values: "+axisValues.mkString(", "))
        cls.variantByAxes(axisValues).getOrElse(cls)
      }
    }

    val variantRules: List[String] = positive.filter(_.startsWith("variant-")).map(_.substring("variant-".length))
    val inventoryIconic = data.get("inventory-iconic").getOrElse("default")
    val animalIconic = data.get("animal-iconic").getOrElse("none")

    println("Given iconic: "+data.get("inventory-iconic").getOrElse("(none)"))
    if (customIconic.isDefined) println("Custom iconic uploaded")
    if (customAnimalIconic.isDefined) println("Custom animal companion iconic uploaded")
    if (customLogo.isDefined) println("Custom logo uploaded")

    def multiSkill(base: String, name: String): List[String] = {
      if (positive.contains("show-"+base)) {
        for (
          n <- (0 until 30).toList;
          if data.contains(base+"-skill-"+n);
          skill <- data.get(base+"-skill-"+n)
        ) yield {
          println("Found skill: "+name+" ("+skill+")")
          name+" ("+skill+")"
        }
      } else Nil
    }

    // data
    CharacterData(
      classes, 
      colour = data.get("colour").getOrElse("normal"),
      spellbookSize = data.get("spellbook-size").getOrElse("medium"),
      inventoryStyle = data.get("inventory-style").getOrElse("auto"),
      inventoryIconic = inventoryIconic,
      customIconic = if (inventoryIconic == "custom") customIconic else None,
      animalIconic = animalIconic,
      customAnimalIconic = if (animalIconic == "custom") customAnimalIconic else None,
      logo = Logo.get(data.get("logo").getOrElse(gameData.game)),
      customLogo = if (data.get("logo") == Some("custom")) customLogo else None,

      permission = positive.contains("permission"),
      buildMyCharacter = positive.contains("build-my-character"),
      includeGM = positive.contains("gm"),
      partyDownload = positive.contains("party-download"),
      hideInventory = positive.contains("simple"),
      moreClasses = positive.contains("more"),
      skillsStyle = data.get("skills-list-style").getOrElse("normal"),
      allKnowledge = positive.contains("all-knowledge"),
      performSkills = multiSkill("perform", "Perform"),
      craftSkills = multiSkill("craft", "Craft"),
      professionSkills = multiSkill("profession", "Profession"),
      // performSkills = if (positive.contains("show-perform")) {
      //   println("Found performance: "+data.get("perform-skill").getOrElse("nothing"))
      //   data.get("perform-skill").map("Perform ("+_+")")
      // } else None,
      // craftSkill = if (positive.contains("show-craft")) {
      //   println("Found craft: "+data.get("craft-skill").getOrElse("nothing"))
      //   data.get("craft-skill").map("Craft ("+_+")")
      // } else None,
      // professionSkill = if (positive.contains("show-profession")) {
      //   println("Found profession: "+data.get("profession-skill").getOrElse("nothing"))
      //   data.get("profession-skill").map("Profession ("+_+")")
      // } else None,
      includeCharacterBackground = positive.contains("include-background"),
      isPathfinderSociety = gameData.isPathfinder && positive.contains("include-pathfinder-society"),
      includeLycanthrope = positive.contains("include-lycanthrope"),
      includeIntelligentItem = positive.contains("include-intelligent-item"),
      includePartyFunds = positive.contains("include-party-funds"),
      includeAnimalCompanion = positive.contains("include-animal-companion"),
      includeMini = positive.contains("include-mini"),
      miniSize = data.get("mini-size").getOrElse("medium"),
      miniAnimalSize = data.get("mini-animal-size").getOrElse("medium"),

      watermark = if (positive.contains("has-watermark")) data.get("watermark").getOrElse("") else "",

      variantRules = variantRules
      )
  }

  def parseParty(data: Map[String, String], gameData: GameData, customLogo: Option[File]): List[CharacterData] = {
    val charids = data.get("charids").getOrElse("").split(",").map(_.trim).filter(_ != "").toList
    println("Stashed character IDs: "+charids.mkString(", "))
    val stashedCharacters = charids.map { charid =>
      val prefix = "char-"+charid+"-"
      val subdata: Map[String, String] = data.filterKeys(_.startsWith(prefix)).map { case (key, value) => key.substring(prefix.length) -> value } toMap;
      parse(subdata, gameData, None, None, customLogo)
    }
    val finalCharacter = parse(data, gameData, None, None, customLogo)
    stashedCharacters ::: (finalCharacter :: Nil)
  }

  def parseGM(data: Map[String, String], gameData: GameData): GMData = {
    val positive = positiveData(data)

    val aps = for (ap <- gameData.gm.aps; if positive.contains("ap-"+ap.code)) yield ap.code
    println("Game APs: "+gameData.gm.aps.map(_.code).mkString(", "))

    GMData(
      colour = data.get("colour").getOrElse("normal"),
      watermark = if (positive.contains("has-watermark")) data.get("watermark").getOrElse("") else "",
      logo = Logo.get(data.get("logo").getOrElse(gameData.game)),

      permission = positive.contains("permission"),
      gmCampaign = positive.contains("gm-campaign"),
      numPCs = data.get("num-pcs").map(_.toInt).getOrElse(4),
      maps = positive.contains("maps"),
      maps3d = data.get("maps-view").getOrElse(if (gameData.isStarfinder) "2d" else "3d") == "3d",
      settlementStyle = data.get("settlement-style").getOrElse("normal"),
      aps = aps
      )
  }
}

case class GMData (
  colour: String,
  watermark: String,
  logo: Option[Logo],

  permission: Boolean,
  gmCampaign: Boolean,
  numPCs: Int,
  maps: Boolean,
  maps3d: Boolean,
  settlementStyle: String,
  aps: List[String]
  )

case class CharacterData (
  classes: List[GameClass],
  colour: String,
  spellbookSize: String,
  inventoryStyle: String,
  inventoryIconic: String,
  customIconic: Option[File],
  animalIconic: String,
  customAnimalIconic: Option[File],
  logo: Option[Logo],
  customLogo: Option[File],

  permission: Boolean,
  buildMyCharacter: Boolean,
  includeGM: Boolean,
  partyDownload: Boolean,
  hideInventory: Boolean,
  moreClasses: Boolean,
  skillsStyle: String,
  allKnowledge: Boolean,
  performSkills: List[String],
  craftSkills: List[String],
  professionSkills: List[String],
  includeCharacterBackground: Boolean,
  isPathfinderSociety: Boolean,
  includeLycanthrope: Boolean,
  includeIntelligentItem: Boolean,
  includePartyFunds: Boolean,
  includeAnimalCompanion: Boolean,
  includeMini: Boolean,
  miniSize: String,
  miniAnimalSize: String,

  watermark: String,

  variantRules: List[String]
) {
  def hasCustomIconic = customIconic.isDefined
  def hasAnimalIconic = animalIconic != "generic" && animalIconic != "none" && !(animalIconic == "custom" && !customAnimalIconic.isDefined)
  def hasCustomLogo = customLogo.isDefined
  def iconic: Option[IconicImage] = IconicImage.get(inventoryIconic)

  def makeEidolon(game: GameData): CharacterData = {
    val eidolonClass = game.classByName("eidolon")

    this.copy(
      classes = eidolonClass.toList
    )
  }

  def makePhantom(game: GameData): CharacterData = {
    val phantomClass = game.classByName("Phantom")

    this.copy(
      classes = phantomClass.toList
    )
  }

  def makeAnimalCompanion(game: GameData): CharacterData = {
    val animalClass = BaseClass("Animal Companion", Some("Animal"), Nil, game.animalSkills)
    this.copy(
      classes = List(animalClass)
    )
  }

  def makeDrone(game: GameData): CharacterData = {
    println("Drone skills: "+game.droneSkills.mkString(", "))
    val droneClass = BaseClass("Drone", Some("Drone"), Nil, game.droneSkills)
    this.copy(
      classes = List(droneClass)
    )
  }

  def makeNPC(game: GameData): CharacterData = {
    val npcClass = BaseClass("NPC", None, Nil, game.coreSkills)
    this.copy(
      classes = List(npcClass)
    )
  }
}
