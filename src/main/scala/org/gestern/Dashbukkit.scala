package org.gestern

import java.io.File

import com.megatome.j2d.DocsetCreator
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.EmtpyCommitException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.rogach.scallop._

import scala.collection.JavaConverters._
import scala.sys.process._
import scala.xml.{Elem, XML}


object Dashbukkit extends App with StrictLogging {

  object Args extends ScallopConf(args) {
    val param = opt[String]("param", default=Some("default"), descr="This is an example parameter")
    val trailing = trailArg[List[String]]("trailing", required=false, default=Some(Nil), descr="An optional trailing argument.")
    verify()
  }

  logger.info("creating docset for bukkit")

  val docsetName = "bukkit"
  val bukkitDir = new File("bukkit/")
  val targetDir = new File("target/")
  val targetFile = new File(targetDir, s"$docsetName.docset")
  val feedFile = new File(s"feed/$docsetName.xml")


  targetDir.mkdir()

  val remote = "https://hub.spigotmc.org/stash/scm/spigot/bukkit.git"

  val gitBukkit =
    try {
      val repoBuilder = new FileRepositoryBuilder()
      val repo = repoBuilder.setMustExist(true).setWorkTree(bukkitDir).build()
      new Git(repo)
    } catch {
      case notFound: RepositoryNotFoundException =>
        Git.cloneRepository().setURI(remote).setDirectory(bukkitDir).call()
    }

  val gitThis = {
    val repoBuilder = new FileRepositoryBuilder()
    val repo = repoBuilder.setMustExist(true).setWorkTree(new File(".")).build()
    new Git(repo)
  }

  val pullResult = gitBukkit.pull.setStrategy(MergeStrategy.THEIRS).call()
  if (pullResult.isSuccessful) {
    val createdJavaDoc = Process("mvn javadoc:javadoc", bukkitDir).!
    if (createdJavaDoc == 0) {
      new DocsetCreator.Builder(docsetName, new File(bukkitDir, "target/site/apidocs"))
        .outputDirectory(targetDir)
        .build()
        .makeDocset()

      val pom = XML.loadFile(new File(bukkitDir, "pom.xml"))
      val version = (pom \\ "project" \ "version").head.text

      val targetTgzFile = targetTgz(version)
      val tar = s"tar --exclude='.DS_Store' -czf ${targetTgzFile.getAbsolutePath} ${targetFile.getAbsolutePath}".!
      assert(tar == 0 && targetTgzFile.isFile)

      val feedData = feedXml(version)
      XML.save(feedFile.getAbsolutePath, feedData)
      gitThis.add().setUpdate(true).addFilepattern("feed/").call()
      gitThis.tag().setForceUpdate(true).setName(s"v$version").call()
      try {
        gitThis.commit().setAllowEmpty(false).setMessage(s"update feed for $version").call()
      } catch {
        case _: EmtpyCommitException => // ignore
      }

      val githubToken = sys.env("GITHUB_TOKEN")
      val credentials = new UsernamePasswordCredentialsProvider(githubToken, "")
      gitThis.push().setPushTags().setRemote("https://github.com/jastice/dash-bukkit.git").setCredentialsProvider(credentials).call()

    }
    else sys.error("error creating javadoc")

  }
  else sys.error("pull failed")

  def targetTgz(version: String) = new File(targetDir, s"$docsetName-$version.tgz")

  def feedXml(version: String): Elem = {
    <entry>
      <version>{version}</version>
      <url>https://github.com/jastice/dash-bukkit/archive/bukkit-{version}.tgz</url>
    </entry>
  }

}
