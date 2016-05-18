package org.gestern

import java.io.{File, FileWriter}

import com.megatome.j2d.DocsetCreator
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.jgit.api.{Git, PushCommand}
import org.eclipse.jgit.api.errors.{EmtpyCommitException, TransportException}
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import scala.sys.process._
import scala.xml.{Elem, XML}


object Dashbukkit extends StrictLogging {

  def main(args: Array[String]) {

    logger.info("creating docset for bukkit")

    val docsetName = "bukkit"

    val targetDir = new File("target/")
    val targetFile = new File(targetDir, s"$docsetName.docset")

    val githubToken = sys.env("GITHUB_TOKEN")
    val credentialsProvider = new UsernamePasswordCredentialsProvider(githubToken, "")

    val feedDir = new File("feed/")
    val feedFile = new File(feedDir, s"$docsetName.xml")
    val feedHashFile = new File(feedDir, ".hash")

    val bukkitRepo = "https://hub.spigotmc.org/stash/scm/spigot/bukkit.git"
    val bukkitDir = new File("bukkit/")
    val bukkitGit = cloneOrUpdate(bukkitRepo, bukkitDir, credentialsProvider)

    val selfGit = {
      val repo = new FileRepositoryBuilder().setMustExist(true).setWorkTree(new File(".")).build()
      new Git(repo)
    }
    // Travis checks out in detached head state
    selfGit.checkout().setName("master").call()

    targetDir.mkdir()

    val bukkitPull = bukkitGit.pull.setStrategy(MergeStrategy.THEIRS).call()

    // something monad-y would be nicer here
    val createdJavaDoc =
      bukkitPull.isSuccessful &&
        Process("mvn javadoc:javadoc", bukkitDir).! == 0

    if (createdJavaDoc) {
      new DocsetCreator.Builder(docsetName, new File(bukkitDir, "target/site/apidocs"))
        .outputDirectory(targetDir)
        .build()
        .makeDocset()

      val pom = XML.loadFile(new File(bukkitDir, "pom.xml"))
      val version = (pom \\ "project" \ "version").head.text

      val targetTgzFile = targetTgz(version)
      val tar = s"tar --exclude='.DS_Store' -czf ${targetTgzFile.getAbsolutePath} ${targetFile.getAbsolutePath}".!
      assert(tar == 0 && targetTgzFile.isFile)

      // hash to make sure an update commit is created iff the release file changes
      val releaseHash = bukkitGit.log().setMaxCount(1).call().iterator().next().getName
      val fw = new FileWriter(feedHashFile, false)
      fw.write(releaseHash)
      fw.close()

      val feedData = feedXml(version)
      XML.save(feedFile.getAbsolutePath, feedData)
      selfGit.add().setUpdate(true).addFilepattern(feedDir.getName).call()
      try {
        selfGit.commit().setAllowEmpty(false).setMessage(s"update feed for $version").call()
      } catch {
        case _: EmtpyCommitException =>
          println("no changes to feed; no commit necessary")
      }

      selfGit.tag().setForceUpdate(true).setName(s"v$version").call()

      attemptPush( selfGit.push().setCredentialsProvider(credentialsProvider) )
      attemptPush( selfGit.push().setCredentialsProvider(credentialsProvider).setPushTags() )

    } else sys.error("error pulling or creating javadoc or something")

    def targetTgz(version: String) = new File(targetDir, s"$docsetName-$version.tgz")
  }

  def attemptPush(cmd: PushCommand) = {
    try { cmd.call() }
    catch {
      case x: TransportException if x.getMessage contains "Nothing to push" =>
        println(x.getMessage)
    }
  }


  def feedXml(version: String): Elem = {
    <entry>
      <version>{version}</version>
      <url>https://github.com/jastice/dash-bukkit/releases/download/v{version}/bukkit-{version}.tgz</url>
    </entry>
  }

  def cloneOrUpdate(remote: String, local: File, credentialsProvider: UsernamePasswordCredentialsProvider) = {
    try {
      val repoBuilder = new FileRepositoryBuilder()
      val repo = repoBuilder.setMustExist(true).setWorkTree(local).build()
      new Git(repo)
    } catch {
      case notFound: RepositoryNotFoundException =>
        Git.cloneRepository().setURI(remote).setDirectory(local).setCredentialsProvider(credentialsProvider).call()
    }
  }

}
