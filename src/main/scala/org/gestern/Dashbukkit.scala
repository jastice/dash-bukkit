package org.gestern

import java.io.File

import com.megatome.j2d.DocsetCreator
import org.rogach.scallop._
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import scala.collection.JavaConverters._
import sys.process._


object Dashbukkit extends App with StrictLogging {

  object Args extends ScallopConf(args) {
    val param = opt[String]("param", default=Some("default"), descr="This is an example parameter")
    val trailing = trailArg[List[String]]("trailing", required=false, default=Some(Nil), descr="An optional trailing argument.")
    verify()
  }

  val docsetName = "bukkit"

  logger.info("creating docset for bukkit")

  val bukkitDir = new File("bukkit/")
  val targetDir = new File("target/")

  targetDir.mkdir()

  val remote = "https://hub.spigotmc.org/stash/scm/spigot/bukkit.git"

  val git =
    try {
      val repoBuilder = new FileRepositoryBuilder()
      val repo = repoBuilder.setMustExist(true).setWorkTree(bukkitDir).build()
      new Git(repo)
    } catch {
      case notFound: RepositoryNotFoundException =>
        Git.cloneRepository().setURI(remote).setDirectory(bukkitDir).call()
    }

  val pullResult = git.pull.setStrategy(MergeStrategy.THEIRS).call()
  if (pullResult.isSuccessful) {
    val createdJavaDoc = Process("mvn javadoc:javadoc", bukkitDir).!
    if (createdJavaDoc == 0) {
      new DocsetCreator.Builder(docsetName, new File(bukkitDir, "target/site/apidocs"))
        .outputDirectory(targetDir).build()
        .makeDocset()

    } else sys.error("error creating javadoc")

  }
  else sys.error("pull failed")

}
