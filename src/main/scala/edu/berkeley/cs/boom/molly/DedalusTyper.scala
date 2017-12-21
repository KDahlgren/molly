package edu.berkeley.cs.boom.molly

import edu.berkeley.cs.boom.molly.ast._
import edu.berkeley.cs.boom.molly.ast.StringLiteral
import edu.berkeley.cs.boom.molly.ast.Expr
import edu.berkeley.cs.boom.molly.ast.Aggregate
import edu.berkeley.cs.boom.molly.ast.Program
import org.jgrapht.alg.util.UnionFind
import scala.collection.JavaConverters._
import org.kiama.util.Positions

// ################################################### //
//KD :
import java.io._
import scala.io.Source
// ################################################### //

sealed trait DedalusType
object DedalusType {
  case object INT extends DedalusType
  case object STRING extends DedalusType
  case object LOCATION extends DedalusType
  case object UNKNOWN extends DedalusType
}
import DedalusType._

/**
 * Type inference algorithm for Dedalus programs.  The basic idea is that we walk the program AST
 * and trace through all uses of variables in order to determine a set of constraints on variables'
 * types and gather evidence in favor of them having certain types.  For example, if we see that
 * two variables are unified in a rule, then those variables must have the same time.  Similarly, if
 * we see a variable unified with an integer constant, then that variable must be of type integer.
 */
object DedalusTyper {

  private type ColRef = (String, Int)  // (tableName, columnNumber) pairs

  private def inferTypeOfAtom(atom: Atom): DedalusType = {
    atom match {
      case StringLiteral(_) => STRING
      case IntLiteral(_) => INT
      case a: Aggregate => INT
      case e: Expr => INT
      case _ => UNKNOWN
    }
  }

  private def inferTypeOfString( aString: String ): DedalusType = {
    aString match {
      case "string" => STRING
      case "int" => INT
    }
  }
  private def dom(types: Set[(Atom, DedalusType)]): Set[(Atom, DedalusType)] = {
    if (types.map(_._2) == Set(STRING, LOCATION)) {
      types.filter(_._2 == LOCATION)
    } else {
      types
    }
  }

  /**
   * Infers the types of predicate columns.
   *
   * The possible column types are 'string', 'int', and 'location'.
   *
   * @param program the program to type
   * @return a copy of the program with its `tables` field filled in.
   */
  def inferTypes(program: Program): Program = {
    require (program.tables.isEmpty, "Program is already typed!")
    val allPredicates = program.facts ++ program.rules.map(_.head) ++
      program.rules.flatMap(_.bodyPredicates)
    val mostColRefs = for (
      pred <- allPredicates;
      (col, colNum) <- pred.cols.zipWithIndex
    ) yield (pred.tableName, colNum) 

    val allColRefs = mostColRefs ++ Seq(
      ("crash", 0),
      ("crash", 1),
      ("crash", 2),
      ("crash", 3),
      ("clock", 0),
      ("clock", 1),
      ("clock", 2),
      ("clock", 3)
    )

    // Determine (maximal) sets of columns that must have the same type:
    val colRefToMinColRef = new UnionFind[ColRef](allColRefs.toSet.asJava)

    // Columns that are related through variable unification must have the same type:
    for (
      rule <- program.rules;
      sameTypedCols <- rule.variablesWithIndexes.groupBy(_._1).values.map(x => x.map(_._2));
      firstColRef = sameTypedCols.head;
      colRef <- sameTypedCols
    ) {
      colRefToMinColRef.union(colRef, firstColRef)
    }

    // Columns that are related through variables appearing in quals must have the same type:
    for (
      rule <- program.rules;
      varToColRef = rule.variablesWithIndexes.groupBy(_._1).mapValues(_.head._2);
      qual <- rule.bodyQuals;
      colRefs = qual.variables.toList.map(i => varToColRef(i.name));
      firstColRef = colRefs.head;
      colRef <- colRefs
    ) {
      colRefToMinColRef.union(colRef, firstColRef)
    }

    // Accumulate all type evidence.  To provide useful error messages when we find conflicting
    // evidence, we store the provenance of this evidence (the Atom
    val typeEvidence: Map[ColRef, Set[(Atom, DedalusType)]] = {
      // Some meta-EDB tables might be empty in certain runs (such as crash()), so we need to
      // hard-code their type evidence:
      val metaEDBTypes = Seq(
        (colRefToMinColRef.find(("crash", 0)), (null, LOCATION)),
        (colRefToMinColRef.find(("crash", 1)), (null, LOCATION)),
        (colRefToMinColRef.find(("crash", 2)), (null, INT)),
        (colRefToMinColRef.find(("crash", 3)), (null, INT)),
        (colRefToMinColRef.find(("clock", 0)), (null, LOCATION)),
        (colRefToMinColRef.find(("clock", 1)), (null, LOCATION)),
        (colRefToMinColRef.find(("clock", 2)), (null, INT)),
        (colRefToMinColRef.find(("clock", 3)), (null, INT))
      )
      val firstColumnTypeIsLocation = for (
        pred <- allPredicates;
        (col, colNum) <- pred.cols.zipWithIndex
        if colNum == 0
      ) yield (colRefToMinColRef.find((pred.tableName, colNum)), (col, LOCATION))
      val inferredFromPredicates = for (
        pred <- allPredicates;
        (col, colNum) <- pred.cols.zipWithIndex;
        inferredType = inferTypeOfAtom(col)
        if inferredType != UNKNOWN
      ) yield (colRefToMinColRef.find((pred.tableName, colNum)), (col, inferredType))
      val evidence = inferredFromPredicates ++ metaEDBTypes ++ firstColumnTypeIsLocation
      evidence.groupBy(_._1).mapValues(_.map(_._2).toSet)
    }

    // Check that all occurrences of a given predicate have the same number of columns:
    val numColsInTable = allPredicates.groupBy(_.tableName).mapValues { predicates =>
      val colCounts = predicates.map(_.cols.size).toSet
      assert(colCounts.size == 1,
        s"Predicate ${predicates.head.tableName} used with inconsistent number of columns in $predicates")
      colCounts.head
    } + ("clock" -> 4) + ("crash" -> 4)

    // ################################################# //
    // KD
    //   read column types from iapyx file and create a map.

    val iapyx_types_file  = "./iapyx_types.data"
    val iapyx_types       = Source.fromFile( iapyx_types_file ).getLines.mkString
    val iapyx_types_array = iapyx_types.split( ";" )

    println( "iapyx_types_array:" )
    println( iapyx_types_array )

    var type_map = scala.collection.mutable.Map[ String, List[ DedalusType ] ]()

    println( "typeLines:" )
    for( type_line <- iapyx_types_array ) {
      println( "type_line:" )
      println( type_line );
      val type_line_array = type_line.split( "," )
      val table_name      = type_line_array(0)
      val table_types     = type_line_array.slice( 1, type_line_array.length )

      // convert array of strings into list of dedalus types 
      var tmp_list            = List[ DedalusType ]()
      val table_types_reverse = table_types.reverse //need to do this. otherwise, results are backwards??? 
                                                    //weird. probably something to do with the '::=' operator.
      for( t <- table_types_reverse ) {
        tmp_list ::= inferTypeOfString( t )
      }

      //val types_list         = table_types.toList
      val types_list         = tmp_list
      type_map( table_name ) = types_list
    }

    println( "type_map:" )
    println( type_map )

    // ################################################# //

    // Assign types to each group of columns:
    val tableNames = allPredicates.map(_.tableName).toSet  ++ Set("crash", "clock")
    val tables = tableNames.map { tableName =>

//    // ################################################# //
//    // KD
//    //   bypassing molly type assignment process
//
//      val numCols = numColsInTable(tableName)
//      val colTypes = (0 to numCols - 1).map { colNum =>
//        val representative = colRefToMinColRef.find((tableName, colNum))
//        val types = dom(typeEvidence.getOrElse(representative,
//          throw new Exception(
//            s"No evidence for type of column ${representative._2} of ${representative._1}")))
//
//        assert(types.map(_._2).size == 1, {
//          val evidenceByType = types.groupBy(_._2)
//          val headPosition =
//            Positions.getStart(allPredicates.filter(_.tableName == tableName).head.cols(colNum))
//          s"Conflicting evidence for type of column $colNum of $tableName:\n\n" +
//          headPosition.longString + "\n---------------------------------------------\n" +
//          evidenceByType.map { case (inferredType, evidence) =>
//            val evidenceLocations = for ((atom, _) <- evidence) yield {
//              if (atom != null) Positions.getStart(atom).longString
//              else "unification with meta EDB column"
//            }
//            s"Evidence for type $inferredType:\n\n" +  evidenceLocations.mkString("\n")
//          }.mkString("\n---------------------------------------------\n")
//        })
//
//        types.head._2
//      }
//
//      // ========================================= //
//
//      println( "----" )
//      println( "tableName" )
//      println( tableName )
//      println( type_map( tableName ) )
//      println( type_map( tableName )(0) )
//
//      println( "colTypes:" )
//      println( colTypes.getClass )
//      println( colTypes )
//
//      val this_type_list = type_map( tableName )
//      val colTypes2      = Vector.empty ++ this_type_list
//
//      println( "colTypes2:" )
//      println( colTypes2.getClass )
//      println( colTypes2 )
//
//      // ========================================= //
//
//      //Table(tableName, colTypes.toList)
//      Table(tableName, colTypes2.toList)
//
//      // ################################################# //

      // ################################################# //
      // KD
      //   save column types from iapyx file instead.

      println( "tableName" )
      println( tableName )

      val this_type_list = type_map( tableName )
      val colTypes2      = Vector.empty ++ this_type_list

      println( "colTypes2:" )
      println( colTypes2 )

      Table( tableName, colTypes2.toList )

      // ################################################# //

    }
    program.copy(tables = tables)
  }
}
